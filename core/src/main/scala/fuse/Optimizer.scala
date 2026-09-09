package fuse
import scala.quoted.*
import CollectionStrategy.*

final class Optimizer[IR <: AnyIR](val ir: IR) {
  private given macroQuotes: ir.quotes.type = ir.quotes

  import ir.*
  import ir.quotes.reflect.*
  import ir.StreamTree.*

  def optimize[OUT, Buf, R](ast: ir.Ast[OUT, Buf, R]): AstExt[OUT, Buf, R] = {

    // Stream optimization
    // val optimizedStream = optimize(ast.parsedStream)

    // Stream enrichment
    val (earlyRef, exitDeclarations) = enrich(ast.collectionStrategy)
    val (enrichedStream, declarations, earlyExitRefs) = enrich[OUT](ast.parsedStream, earlyRef.toList)

    // Verifica se gli indici di produzione sono 1:1 con quelli della fonte
    val hasAlignedIndexes = checkAlignedIndexes(enrichedStream)
    // Estrae, se è presente, l'esrpressione che definisce l'upper bound della fonte
    val hasSourceSize = hasKnownSourceSize(enrichedStream)
    AstExt(
      enrichedStream,
      ast.prefixStatements ::: declarations ::: exitDeclarations,
      EnrichedCollectionStrategy(ast.collectionStrategy, earlyRef, earlyExitRefs),
      hasAlignedIndexes,
      hasSourceSize
    )
  }

  private def enrich[OUT, Buf, R](strategy: CollectionStrategy[OUT, Buf, R]): (Option[Expr[Boolean]], List[Statement]) = {

    strategy match {
      // If is a toArray/Summing strategy no enrichment is needed it will be handled completely in the code generation phase with native types specializtion
      case ToArray() => (None, Nil)
      case Summing() => (None, Nil)
      // Here we have to emit the variable for exiting the stream: the declaration and the  expsression that links it
      case WithCollector(collector) => {
        val collectorTpe: TypeRepr = collector.asTerm.tpe
        val earlyStoppingType = TypeRepr.of[fuse.EarlyStopping]
        val isEarlyStopping = collectorTpe <:< earlyStoppingType
        println(s"With collector: ${collector.getClass()} early stop: ${isEarlyStopping}")
        if (isEarlyStopping) {

          // Create a dedicated variable for exit
          val counterSymbol = createVariable[Boolean]("keepProducing")
          val valDef = createDef(counterSymbol, true)
          val counterRef = Ref(counterSymbol).asExprOf[Boolean]
          return (Option(counterRef), List(valDef))
        } else {
          return (None, Nil)
        }
      }

    }
  }

  // def optimize[OUT](parsedStream: StreamTree[Phase.RawOUT]): StreamTree[OUT] = {
  //   println(s"[Optimizing Node] => $parsedStream")

  //   parsedStream match {
  //     // TODO: change limit and skip to a slice, maybe at parse time

  //     // Both limit and skip have no possible optimizations
  //     case Slice(upstream, count, count2, outType) => Slice(optimize(upstream), count, count2, outType)

  //     case map: Map[in, OUT] => Map[in, OUT](upstream = optimize(map.upstream), function = map.function, inType = map.inType, outType = map.outType)

  //     case filter: Filter[OUT] => Filter[OUT](upstream = optimize(filter.upstream), predicate = filter.predicate, outType = filter.outType)

  //     case flatMap: FlatMap[in, OUT] =>
  //       FlatMap(optimize(flatMap.upstream), optimize(flatMap.innerTree), flatMap.inType, flatMap.outType, flatMap.elemSymbol, flatMap.innerDeclarations)

  //     // ========== Root nodes ==========
  //     case source: JListSource[in]     => source
  //     case source: ArraySource[in]     => source
  //     case source: EnrichedJListSource[in]     => source
  //     case source: EnrichedArraySource[in]     => source
  //     case source: IterableSource[in]  => source // It's the root nodw
  //     case source: JIterableSource[in] => source // It's the root nodw

  //   }

  // }

  /** Navigate bottom up enriching the components
    *
    * @param parsedStream
    */
  private def enrich[OUT](parsedStream: StreamTree[Phase.Raw, OUT], exitPredicates: List[Expr[Boolean]])(using
      Quotes
  ): (StreamTree[Phase.Enriched, OUT], List[Statement], List[Expr[Boolean]]) = {

    // Recursevly enrich upstream (bottom up)
    println(s"[Enriching Node] => $parsedStream")

    parsedStream match {
      case slice: Slice[OUT] => {
        // Evaluate slice bounds exactly once
        val (fromRef, fromDeclarations) = slice.from match {
          case Some(from) => {
            val (ref, definition) = materializeInt("sliceFrom", from)
            (Some(ref), List(definition))
          }
          case None => (None, Nil)
        }

        val (untilRef, untilDeclarations) = slice.until match {
          case Some(until) => {
            val (ref, definition) = materializeInt("sliceUntil", until)
            (Some(ref), List(definition))
          }
          case None => (None, Nil)
        }

        // Single position counter for the whole slice
        val counterSymbol = createVariable[Int]("sliceCounter")
        val counterDef = createDef(counterSymbol, 0)
        val counterRef = Ref(counterSymbol).asExprOf[Int]

        // `until` can stop traversal completely, so bubble it down to the source.
        val currentExitPredicates = untilRef match {
          case Some(until) => '{ $counterRef < $until } :: exitPredicates
          case None        => exitPredicates
        }

        val (enrichedUpstream, upstreamDeclarations, outPred) = enrich(slice.upstream, currentExitPredicates)

        val enrichedSlice = EnrichedSlice[OUT](upstream = enrichedUpstream, from = fromRef, until = untilRef, outType = slice.outType, counterRef = counterRef)

        (enrichedSlice, upstreamDeclarations ::: fromDeclarations ::: untilDeclarations ::: List(counterDef), outPred)
      }

      case map: Map[Phase.Raw, in, OUT] => {
        given Type[in] = map.inType
        given Type[OUT] = map.outType

        // Materialize the function, if it's somthing like makeMapper() we create the mapper only once and assign it to a constant
        val (functionRef, functionDeclarations) = materializeFunction[in, OUT]("mapFunction", map.function)
        val (enrichedUpstream, declarations, predicates) = enrich[in](map.upstream, exitPredicates)
        val enrichedMap = Map[Phase.Enriched, in, OUT](enrichedUpstream, functionRef, map.inType, map.outType)
        (enrichedMap, declarations ::: functionDeclarations, predicates)
      }

      case Filter(upstream, predicate, outType) => {
        given Type[OUT] = outType
        // Materialize the function, if it's somthing like makeFilter() we create the mapper only once and assign it to a constant
        val (predicateRef, predicateDeclarations) = materializeFunction[OUT, Boolean]("filterPredicate", predicate)
        val (enrichedUpstream, declarations, predicates) = enrich[OUT](upstream, exitPredicates)
        val enrichedFilter = Filter[Phase.Enriched, OUT](enrichedUpstream, predicateRef, outType)
        (enrichedFilter, declarations ::: predicateDeclarations, predicates)
      }

      case fm: FlatMap[in, OUT] => {

        // Enriche the inner stream, already extracted during the optimization phase, gathers exitPredicates + local predicates to the inner
        val (innerEnrichedTree, innerDecls, innerAccumulatedPredicates) = enrich[OUT](fm.innerTree, exitPredicates)
        val allInnerDecls = fm.innerDeclarations ++ innerDecls
        // Recurisvy Enrich the upstream upstream using the predicates from the level of the flatmap
        val (enrichedUpstream, upstreamDeclarations, fullExitPredicates) = enrich[in](fm.upstream, exitPredicates)

        val enrichedFlatMap =
          EnrichedFlatMap[in, OUT](
            upstream = enrichedUpstream,
            innerTree = innerEnrichedTree,
            inType = fm.inType,
            outType = fm.outType,
            elemSymbol = fm.elemSymbol,
            innerDeclarations = allInnerDecls,
            predicates = innerAccumulatedPredicates
          )

        (enrichedFlatMap, upstreamDeclarations, fullExitPredicates)
      }

      // ============ Root nodes ============
      case IterableSource(term, outType) => (IterableSource[Phase.Enriched, OUT](term, outType), Nil, exitPredicates)

      case JIterableSource(term, outType) => (JIterableSource[Phase.Enriched, OUT](term, outType), Nil, exitPredicates)
      
      case source: ArraySource[OUT] =>
        given Type[OUT] = source.outType
        val sourceSymbol = createConstant[Array[OUT]]("source")
        val sourceRef = Ref(sourceSymbol).asExprOf[Array[OUT]]
        val sourceDef = ValDef(sourceSymbol, Some(source.term.asTerm))
        val sizeSymbol = createConstant[Int]("sourceSize")
        val sizeRef = Ref(sizeSymbol).asExprOf[Int]
        val sizeDef = ValDef(sizeSymbol, Some('{ $sourceRef.length }.asTerm))
        (EnrichedArraySource[OUT](sourceRef, sizeRef, source.outType), List[Statement](sourceDef, sizeDef), exitPredicates)

      case source: JListSource[OUT] =>
        given Type[OUT] = source.outType
        val sourceSymbol = createConstant[java.util.List[OUT]]("source")
        val sourceRef = Ref(sourceSymbol).asExprOf[java.util.List[OUT]]
        val sourceDef = ValDef(sourceSymbol, Some(source.term.asTerm))
        val sizeSymbol = createConstant[Int]("sourceSize")
        val sizeRef = Ref(sizeSymbol).asExprOf[Int]
        val sizeDef = ValDef(sizeSymbol, Some('{ $sourceRef.size() }.asTerm))
        (EnrichedJListSource[OUT](sourceRef, sizeRef, source.outType), List[Statement](sourceDef, sizeDef), exitPredicates)

    }

  }

  /** Materializa an expression of int into a constant, this permits to avoid double calls
    */
  private def materializeInt(name: String, value: Expr[Int]): (Expr[Int], Statement) = {
    val symbol = createConstant[Int](name)
    val definition = ValDef(symbol, Some(value.asTerm))
    val ref = Ref(symbol).asExprOf[Int]
    (ref, definition)
  }

  private def materializeFunction[IN: Type, OUT: Type](name: String, function: Expr[IN => OUT]): (Expr[IN => OUT], List[Statement]) = {

    if (isDirectLambda(function.asTerm)) {
      (function, Nil)
    } else {
      val symbol = createConstant[IN => OUT](name)
      val definition = ValDef(symbol, Some(function.asTerm))
      val ref = Ref(symbol).asExprOf[IN => OUT]

      (ref, List(definition))
    }
  }

  private def isDirectLambda(term: Term): Boolean = {
    term match {
      case Inlined(_, Nil, inner) => isDirectLambda(inner)
      // Type  Block(List(DefDef("$anonfun", ...)), Closure(...))
      case Block(statements, _: Closure) => statements.forall(_.isInstanceOf[DefDef])
      case Lambda(_, _)                  => true
      case _                             => false
    }
  }

  /** Verifica ricorsivamente se lo stream conserva una corrispondenza posizionale sorgente e accumulatore
    */
  private def checkAlignedIndexes(tree: StreamTree[Phase.Enriched,?]): Boolean = {
    tree match {
      // Radici supportate con indice 0..len-1 nativo
      case _: EnrichedJListSource[?]         => true
      case _: EnrichedArraySource[?]         => true

      // Trasformazione 1:1 che preserva l'indice (propaga a monte)
      case map: Map[Phase.Enriched,?, ?] => checkAlignedIndexes(map.upstream)

      // Operazioni che alterano cardinalità o offset
      case _: Filter[Phase.Enriched,?]     => false
      case _: EnrichedSlice[?]      => false
      case _: EnrichedFlatMap[?, ?] => false

      // Radici basate su iteratore (senza indice contiguo)
      case _: IterableSource[Phase.Enriched,?]  => false
      case _: JIterableSource[Phase.Enriched,?] => false
    }
  }

  private def hasKnownSourceSize(tree: StreamTree[Phase.Enriched,?]): Boolean = {
    tree match {
      case _: EnrichedJListSource[?] => true
      case _: EnrichedArraySource[?] => true

      case _: IterableSource[Phase.Enriched,?]  => false
      case _: JIterableSource[Phase.Enriched,?] => false

      case map: Map[Phase.Enriched,?, ?]    => hasKnownSourceSize(map.upstream)
      case filter: Filter[Phase.Enriched,?] => hasKnownSourceSize(filter.upstream)
      case slice: EnrichedSlice[?]   => hasKnownSourceSize(slice.upstream)

      case _: EnrichedFlatMap[?, ?] => false
    }
  }

}
