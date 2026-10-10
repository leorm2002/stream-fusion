package fuse.internal
import scala.quoted.*
import CollectionStrategy.*
import fuse.internal.ir.{AnyIR, Phase}
import fuse.internal.{CollectionStrategy}

private final class Optimizer[IR <: AnyIR](val ir: IR, val logger: FusedLogger) {
  private given macroQuotes: ir.quotes.type = ir.quotes

  import ir.*
  import ir.quotes.reflect.*
  import ir.StreamTree.*
  import logger.*

  def optimize[OUT, Buf, R](ast: ir.Ast[Phase.Raw, OUT, R]): Ast[Phase.Enriched, OUT, R] = {

    // Stream enrichment
    val (earlyRef, exitDeclarations) = enrich(ast.fields.collectionStrategy.strategy)
    val (enrichedStream, declarations, earlyExitRefs) = enrich[OUT](ast.parsedStream, earlyRef.toList)

    debug(s"[Enriched] => $enrichedStream")
    debug(s"[Declarations] => $declarations")
    debug(s"[ExitDeclarations] => $exitDeclarations")

    // Verifica se gli indici di produzione sono 1:1 con quelli della fonte, Estrae, se è presente, l'esrpressione che definisce l'upper bound della fonte
    val streamInfo = analyze(enrichedStream)
    debug(s"[streamInfo] => $streamInfo")

    var enrichedStrategy = PhasedCollectionStrategy[Phase.Enriched, OUT, R](ast.fields.collectionStrategy.strategy, EnrichedStrategyFields(earlyRef, earlyExitRefs))
    val enriched: Ast[Phase.Enriched, OUT, R] =
      Ast[Phase.Enriched, OUT, R](
        enrichedStream,
        prefixStatements = ast.prefixStatements,
        executionMode = ast.executionMode,
        fields = EnrichedAstFields(
          collectionStrategy = enrichedStrategy,
          declarations = declarations ::: exitDeclarations,
          hasAlignedIndexes = streamInfo.hasAlignedIndexes,
          cardinality = streamInfo.cardinality
        )
      )
    enriched
  }

  private def enrich[OUT, R](strategy: CollectionStrategy[OUT, R]): (Option[Expr[Boolean]], List[Declaration]) = {

    strategy match {
      // If is a toArray/Summing strategy no enrichment is needed it will be handled completely in the code generation phase with native types specializtion
      case ToArray() => (None, Nil)
      case Summing() => (None, Nil)
      // Here we have to emit the variable for exiting the stream: the declaration and the  expsression that links it
      case WithCollector(collector, isEarlyStopping) => {
        debug(s"With collector: ${collector.getClass()} early stop: ${isEarlyStopping}")
        if (isEarlyStopping) {
          // Create a dedicated variable for exit
          val typedCounterSymbol = createVariable[Boolean]("keepProducing")
          val counterRef = typedCounterSymbol.ref
          return (Option(counterRef), List(Declaration.Impl(typedCounterSymbol, Expr(true))))
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
    debug(s"[Enriching Node] => $parsedStream")

    parsedStream match {
      case slice: Slice[Phase.Raw, OUT] => {
        // Evaluate slice bounds exactly once and validate non-negative bounds
        val (fromRef, fromDeclarations) = slice.from match {
          case Some(from) => {
            val (ref, definition) = materializeInt(
              "sliceFrom",
              '{
                val v = $from
                if (v < 0) throw new IllegalArgumentException(s"skip count cannot be negative: $v")
                v
              }
            )
            (Some(ref), List(definition))
          }
          case None => (None, Nil)
        }

        val (untilRef, untilDeclarations) = slice.until match {
          case Some(until) => {
            val (ref, definition) = materializeInt(
              "sliceUntil",
              '{
                val v = $until
                if (v < 0) throw new IllegalArgumentException(s"limit count cannot be negative: $v")
                v
              }
            )
            (Some(ref), List(definition))
          }
          case None => (None, Nil)
        }

        // Single position counter for the whole slice
        val typedCounterSymbol = createVariable[Int]("sliceCounter")
        val counterDef = Declaration.Impl(typedCounterSymbol, Expr(0))
        val counterRef = typedCounterSymbol.ref

        // `until` can stop traversal completely, so bubble it down to the source.
        val currentExitPredicates = untilRef match {
          case Some(until) => '{ $counterRef < $until } :: exitPredicates
          case None        => exitPredicates
        }

        val (enrichedUpstream, upstreamDeclarations, outPred) = enrich(slice.upstream, currentExitPredicates)

        val enrichedSlice =
          Slice[Phase.Enriched, OUT](upstream = enrichedUpstream, from = fromRef, until = untilRef, outType = slice.outType, EnrichedSliceFields(typedCounterSymbol))

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

      case fm: FlatMap[Phase.Raw, in, OUT] => {

        // Enriche the inner stream, already extracted during the optimization phase, gathers exitPredicates + local predicates to the inner
        val (innerEnrichedTree, innerDecls, innerAccumulatedPredicates) = enrich[OUT](fm.innerStream, exitPredicates)
        // Recurisvy Enrich the upstream upstream using the predicates from the level of the flatmap
        val (enrichedUpstream, upstreamDeclarations, fullExitPredicates) = enrich[in](fm.upstream, exitPredicates)

        val enrichedFlatMap = FlatMap[Phase.Enriched, in, OUT](
          upstream = enrichedUpstream,
          innerStream = innerEnrichedTree,
          inType = fm.inType,
          outType = fm.outType,
          binder = fm.binder,
          innerDeclarations = fm.innerDeclarations,
          EnrichedFlatMapFields(innerDecls, innerAccumulatedPredicates)
        )

        (enrichedFlatMap, upstreamDeclarations, fullExitPredicates)
      }

      // ============ Root nodes ============
      case IterableSource(term, outType) => (IterableSource[Phase.Enriched, OUT](term, outType), Nil, exitPredicates)

      case JIterableSource(term, outType) => (JIterableSource[Phase.Enriched, OUT](term, outType), Nil, exitPredicates)

      case source: ArraySource[Phase.Raw, OUT] =>
        given Type[OUT] = source.outType
        val typedSourceSymbol = createConstant[Array[OUT]]("source")
        val sourceRef = typedSourceSymbol.ref
        val sourceDef = Declaration.Impl(typedSourceSymbol, source.term)
        val typedSizeSymbol = createConstant[Int]("sourceSize")
        val sizeRef = typedSizeSymbol.ref
        val sizeDef = Declaration.Impl(typedSizeSymbol, '{ $sourceRef.length })
        (ArraySource[Phase.Enriched, OUT](sourceRef, source.outType, EnrichedArraySourceFields(sizeRef)), List[Declaration](sourceDef, sizeDef), exitPredicates)

      case source: JListSource[Phase.Raw, OUT] =>
        given Type[OUT] = source.outType
        val typedSourceSymbol = createConstant[java.util.List[OUT]]("source")
        val sourceRef = typedSourceSymbol.ref
        val sourceDef = Declaration.Impl(typedSourceSymbol, source.term)
        val typedSizeSymbol = createConstant[Int]("sourceSize")
        val sizeRef = typedSizeSymbol.ref
        val sizeDef = Declaration.Impl(typedSizeSymbol, '{ $sourceRef.size() })
        (JListSource[Phase.Enriched, OUT](sourceRef, source.outType, EnrichedJListFields(sizeRef)), List[Declaration](sourceDef, sizeDef), exitPredicates)

    }

  }

  /** Materialize an expression of Int into a constant, avoiding redundant evaluations */
  private def materializeInt(name: String, value: Expr[Int]): (Expr[Int], Declaration) = {
    val typedSymbol = createConstant[Int](name)
    val ref = typedSymbol.ref
    (ref, Declaration.Impl(typedSymbol, value))
  }

  private def materializeFunction[IN: Type, OUT: Type](name: String, function: Expr[IN => OUT]): (Expr[IN => OUT], List[Declaration]) = {
    if (isDirectLambda(function.asTerm)) {
      (function, Nil)
    } else {
      val typedSymbol = createConstant[IN => OUT](name)
      val ref = typedSymbol.ref
      (ref, List(Declaration.Impl(typedSymbol, function)))
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
      case JListSource(_, _, EnrichedJListFields(sizeRef))       => StreamProperties(Cardinality.Exact(sizeRef), true)
      case ArraySource(_, _, EnrichedArraySourceFields(sizeRef)) => StreamProperties(Cardinality.Exact(sizeRef), true)

      case _: IterableSource[Phase.Enriched, ?]  => StreamProperties(Cardinality.Unknown, false)
      case _: JIterableSource[Phase.Enriched, ?] => StreamProperties(Cardinality.Unknown, false)

      case map: Map[Phase.Enriched, ?, ?]    => analyze(map.upstream)
      case filter: Filter[Phase.Enriched, ?] => {
        val upstream = analyze(filter.upstream)
        upstream.copy(hasAlignedIndexes = false, cardinality = upstream.cardinality.asUpperBound)
      }
      // potenzialmente possiamo accumulare le ref alle variabili e definire la size in maniera esatta
      case slice: Slice[Phase.Enriched, ?] => {
        val upstream = analyze(slice.upstream)
        upstream.copy(hasAlignedIndexes = slice.from.isEmpty && upstream.hasAlignedIndexes, cardinality = upstream.cardinality.asUpperBound)
      }

      case _: FlatMap[Phase.Enriched, ?, ?] => StreamProperties(Cardinality.Unknown, false)
    }
  }
}
