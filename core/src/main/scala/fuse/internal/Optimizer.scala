package fuse.internal
import scala.quoted.*
import CollectionStrategy.*
import fuse.internal.ir.{AnyIR, Phase}
import fuse.internal.{CollectionStrategy, EnrichedCollectionStrategy}

private final class Optimizer[IR <: AnyIR](val ir: IR) {
  private given macroQuotes: ir.quotes.type = ir.quotes

  import ir.*
  import ir.quotes.reflect.*
  import ir.StreamTree.*

  def optimize[OUT, Buf, R](ast: ir.Ast[OUT, Buf, R]): AstExt[OUT, Buf, R] = {

    // Stream enrichment
    val (earlyRef, exitDeclarations) = enrich(ast.collectionStrategy)
    val (enrichedStream, declarations, earlyExitRefs) = enrich[OUT](ast.parsedStream, earlyRef.toList)

    // Verifica se gli indici di produzione sono 1:1 con quelli della fonte, Estrae, se è presente, l'esrpressione che definisce l'upper bound della fonte
    val streamInfo = analyze(enrichedStream)
    
    AstExt(
      enrichedStream,
      ast.prefixStatements,
      declarations ::: exitDeclarations,
      EnrichedCollectionStrategy(ast.collectionStrategy, earlyRef, earlyExitRefs),
      streamInfo.hasAlignedIndexes,
      streamInfo.cardinality,
      ast.executionMode
    )
  }

  private def enrich[OUT, Buf, R](strategy: CollectionStrategy[OUT, Buf, R]): (Option[Expr[Boolean]], List[Declaration]) = {

    strategy match {
      // If is a toArray/Summing strategy no enrichment is needed it will be handled completely in the code generation phase with native types specializtion
      case ToArray() => (None, Nil)
      case Summing() => (None, Nil)
      // Here we have to emit the variable for exiting the stream: the declaration and the  expsression that links it
      case WithCollector(collector,isEarlyStopping) => {
        println(s"With collector: ${collector.getClass()} early stop: ${isEarlyStopping}")
        if (isEarlyStopping) {
          // Create a dedicated variable for exit
          val counterSymbol = createVariable[Boolean]("keepProducing")
          val counterRef = Ref(counterSymbol).asExprOf[Boolean]
          return (Option(counterRef), List(Declaration.Impl(counterSymbol, Expr(true))))
        } else {
          return (None, Nil)
        }
      }

    }
  }

  /** Navigate bottom up enriching the components
    *
    * @param parsedStream
    */
  private def enrich[OUT](
      parsedStream: StreamTree[Phase.Raw, OUT],
      exitPredicates: List[Expr[Boolean]]
  ): (StreamTree[Phase.Enriched, OUT], List[Declaration], List[Expr[Boolean]]) = {

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
        val counterDef = Declaration.Impl(counterSymbol, Expr(0))
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
        // Recurisvy Enrich the upstream upstream using the predicates from the level of the flatmap
        val (enrichedUpstream, upstreamDeclarations, fullExitPredicates) = enrich[in](fm.upstream, exitPredicates)

        val enrichedFlatMap =
          EnrichedFlatMap[in, OUT](
            upstream = enrichedUpstream,
            innerTree = innerEnrichedTree,
            inType = fm.inType,
            outType = fm.outType,
            elemSymbol = fm.elemSymbol,
            innerDeclarations = fm.innerDeclarations,
            innerMaterialized = innerDecls,
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
        val sourceDef = Declaration.Impl(sourceSymbol, source.term)
        val sizeSymbol = createConstant[Int]("sourceSize")
        val sizeRef = Ref(sizeSymbol).asExprOf[Int]
        val sizeDef = Declaration.Impl(sizeSymbol, '{ $sourceRef.length })
        (EnrichedArraySource[OUT](sourceRef, sizeRef, source.outType), List[Declaration](sourceDef, sizeDef), exitPredicates)

      case source: JListSource[OUT] =>
        given Type[OUT] = source.outType
        val sourceSymbol = createConstant[java.util.List[OUT]]("source")
        val sourceRef = Ref(sourceSymbol).asExprOf[java.util.List[OUT]]
        val sourceDef = Declaration.Impl(sourceSymbol, source.term)
        val sizeSymbol = createConstant[Int]("sourceSize")
        val sizeRef = Ref(sizeSymbol).asExprOf[Int]
        val sizeDef = Declaration.Impl(sizeSymbol, '{ $sourceRef.size() })
        (EnrichedJListSource[OUT](sourceRef, sizeRef, source.outType), List[Declaration](sourceDef, sizeDef), exitPredicates)

    }

  }

  /** Materializa an expression of int into a constant, this permits to avoid double calls
    */
  private def materializeInt(name: String, value: Expr[Int]): (Expr[Int], Declaration) = {
    val symbol = createConstant[Int](name)
    val ref = Ref(symbol).asExprOf[Int]
    (ref, Declaration.Impl(symbol, value))
  }

  private def materializeFunction[IN: Type, OUT: Type](name: String, function: Expr[IN => OUT]): (Expr[IN => OUT], List[Declaration]) = {
    if (isDirectLambda(function.asTerm)) {
      (function, Nil)
    } else {
      val symbol = createConstant[IN => OUT](name)
      val ref = Ref(symbol).asExprOf[IN => OUT]
      (ref, List(Declaration.Impl(symbol, function)))
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

  final case class StreamProperties(cardinality: Cardinality, hasAlignedIndexes: Boolean)

  private def analyze(tree: StreamTree[Phase.Enriched, ?]): StreamProperties = {
    tree match {
      case EnrichedJListSource(term, sizeRef, outType) => StreamProperties(Cardinality.Exact(sizeRef), true)
      case EnrichedArraySource(term, sizeRef, outType) => StreamProperties(Cardinality.Exact(sizeRef), true)

      case _: IterableSource[Phase.Enriched, ?]  => StreamProperties(Cardinality.Unknown, false)
      case _: JIterableSource[Phase.Enriched, ?] => StreamProperties(Cardinality.Unknown, false)

      case map: Map[Phase.Enriched, ?, ?]    => analyze(map.upstream)
      case filter: Filter[Phase.Enriched, ?] => {
        val upstream = analyze(filter.upstream)
        upstream.copy(hasAlignedIndexes = false, cardinality = upstream.cardinality.asUpperBound)
      }
      // TODO: potenzialmente possiamo accumulare le ref alle variabili e definire la size in maniera esatta
      case slice: EnrichedSlice[?] => {
        val upstream = analyze(slice.upstream)
        upstream.copy(hasAlignedIndexes = slice.from.isEmpty && upstream.hasAlignedIndexes, cardinality = upstream.cardinality.asUpperBound)
      }

      case _: EnrichedFlatMap[?, ?] => StreamProperties(Cardinality.Unknown, false)
    }
  }
}
