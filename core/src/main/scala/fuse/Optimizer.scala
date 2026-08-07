package fuse
import scala.quoted.*
import scala.compiletime.ops.int

final class Optimizer[IR <: AnyIR](val ir: IR) {
  private given macroQuotes: ir.quotes.type = ir.quotes

  import ir.*
  import ir.quotes.reflect.*

  def optimize[A, Buf, R](ast: ir.Ast[A, Buf, R]): AstExt[A, Buf, R] = {

    // Stream optimization
    val optimizedStream = optimize(ast.parsedStream)

    // Stream enrichment
    val (earlyRef, exitDeclarations) = enrich(ast.collectionStrategy)
    val (enrichedStream, declarations, earlyExitRefs) = enrich[A](optimizedStream, earlyRef.toList)

    AstExt(enrichedStream, declarations ::: exitDeclarations, EnrichedCollectionStrategy(ast.collectionStrategy, earlyRef, earlyExitRefs))
  }

  private def enrich[A, Buf, R](strategy: CollectionStrategy[A, Buf, R]): (Option[Expr[Boolean]], List[Statement]) = {

    strategy match {
      // If is a toArray strategy no enrichment is needed it will be handled completely in the code generation phase with native types specializtion
      case ToArray() => (None, Nil)
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

  def optimize[A](parsedStream: StreamTree[A]): StreamTree[A] = {
    println(s"[Optimizing Node] => $parsedStream")

    parsedStream match {
      // TODO: here match on the node: if it's a node which opens a new scope or a nested stream? (ex flatMap) stop the bubbling

      // Both limit and skip have no possible optimizations
      case Limit(upstream, count, outType) => Limit(optimize(upstream), count, outType)
      case Skip(upstream, count, outType)  => Skip(optimize(upstream), count, outType)

      // Map fuse, concatenate two sequential mapping
      case map2 @ Map(map1 @ Map(upstream, f, inTypeA, outTypeB), g, _, outTypeC) => {
        // m2.function è Expr[b => c]
        (map1, map2) match {
          case (m1: Map[a, b], m2: Map[?, c]) => {
            given Type[a] = m1.inType
            given Type[b] = m1.outType
            given Type[c] = m2.outType

            // f: Expr[a => b]
            // g: Expr[b => c]
            val fusedFunction: Expr[a => c] = '{ (x: a) =>
              ${ Expr.betaReduce('{ ${ m2.function }(${ Expr.betaReduce('{ ${ m1.function }(x) }) }) }) }
            }

            val fusedMap = Map[a, c](upstream.asInstanceOf[StreamTree[a]], fusedFunction, m1.inType, m2.outType)
            // Recursively call the optimize on the new obtained map
            optimize(fusedMap)
          }
        }
      }
      // A map alone no optimizations
      case Map(upstream, function, b, c) => {
        Map(optimize(upstream), function, b, c)
      }
      // Filter fuse, concatenate two sequential filtering
      case f2 @ Filter(f1 @ Filter(upstream, p1, inType1), p2, inType2) => {
        // Beta reducing the filter operations
        (f1, f2) match {
          case (filter1: Filter[a], filter2: Filter[?]) => {
            given Type[a] = filter1.outType

            // La nuova lambda fusa prende (x: a) e usa il corto circuito logico (&&)
            val fusedPredicate: Expr[a => Boolean] = '{ (x: a) => ${ Expr.betaReduce('{ ${ filter1.predicate }(x) }) } && ${ Expr.betaReduce('{ ${ filter2.predicate }(x) }) } }
            val fusedFilter = Filter[a](upstream.asInstanceOf[StreamTree[A]], fusedPredicate, f1.outType)
            // Recursively call the optimize on the new obtained filter
            optimize(fusedFilter)
          }
        }
      }
      case filter @ Filter(upstream, predicate, b) => {
        Filter[A](optimize(upstream), predicate, b)
      }

      case FlatMap(upstream, innerTree, inType, outType, elemSymbol, innerDeclarations) =>

        type IN = Any
        type OUT = A
        given Type[IN] = inType.asInstanceOf[Type[IN]]
        given Type[OUT] = outType.asInstanceOf[Type[OUT]]

        val elemSym = createConstant[IN]("outerElem")

        // Optimize the inner stream itself
        val optimizedInnerTree = optimize(innerTree)

        // Ricostruiamo la lambda ottimizzata o preserviamo il nodo con l'inner stream già parsato/ottimizzato
        val optimizedUpstream = optimize(upstream)

        FlatMap(optimizedUpstream, optimizedInnerTree, inType, outType, elemSymbol, innerDeclarations)

      case source @ ArraySource(_, _)    => source // It's the root nodw
      case source @ IterableSource(_, _) => source // It's the root nodw

    }

  }

  /** Navigate bottom up enriching the components
    *
    * @param parsedStream
    */
  private def enrich[A](parsedStream: StreamTree[A], exitPredicates: List[Expr[Boolean]])(using Quotes): (StreamTree[A], List[Statement], List[Expr[Boolean]]) = {

    // Recursevly enrich upstream (bottom up)
    println(s"[Enriching Node] => $parsedStream")

    parsedStream match {
      // TODO: here match on the node: if it's a node which opens a new scope or a nested stream? (ex flatMap) stop the bubbling
      case Limit(upstream, count, outType) => {
        // Enrich the upstream first

        // Now create the counter state
        val counterSymbol = createVariable[Int]("limitCounter")
        val valDef = createDef(counterSymbol, 0)
        val counterRef = Ref(counterSymbol).asExprOf[Int]

        // Build the predicate associated with the symbol
        val localPredicate: Expr[Boolean] = '{ $counterRef < ${ Expr(count) } }
        val currentExitPredicates = localPredicate :: exitPredicates
        val (enrichedUpstream, upstreamDeclarations, outPred) = enrich(upstream, currentExitPredicates)
        // Step C: Construct enriched node holding the reference
        val enrichedLimit = EnrichedLimit(
          upstream = enrichedUpstream,
          count = count,
          outType = outType,
          counterRef = counterRef
        )

        // Bubble up the variable declarations plus the hoters
        (enrichedLimit, upstreamDeclarations :+ valDef, outPred)
      }

      case Skip(upstream, count, outType) => {
        val counterSymbol = createVariable[Int]("skipCounter")
        val valDef = createDef(counterSymbol, 0)
        val counterRef = Ref(counterSymbol).asExprOf[Int]

        // Enrich the upstream first
        val (enrichedUpstream, upstreamDeclarations, outPred) = enrich(upstream, exitPredicates)
        // Construct enriched node holding the reference
        val enrichedSkip = EnrichedSkip(enrichedUpstream, count, outType, counterRef)

        // Bubble up the variable declarations plus the hoters
        (enrichedSkip, upstreamDeclarations :+ valDef, outPred)
      }

      case Map(upstream, a, b, c) => {
        val (enrichedUpstream, upstreamDeclarations, outPred) = enrich(upstream, exitPredicates)
        val enrichedMap = Map(enrichedUpstream, a, b, c)
        (enrichedMap, upstreamDeclarations, outPred)
      }

      case Filter(upstream, a, b) => {
        val (enrichedUpstream, upstreamDeclarations, outPred) = enrich(upstream, exitPredicates)
        val enrichedFilter = Filter[A](enrichedUpstream, a, b)
        (enrichedFilter, upstreamDeclarations, outPred)

      }

      case source @ IterableSource(_, _) => (source, Nil, exitPredicates) // It's the root nodw
      case source @ ArraySource(_, _)    => (source, Nil, exitPredicates) // It's the root nodw

      case fm @ FlatMap(upstream, optimizedInnerTree, inType, outType, elemSymbol, declarations) => {
        type A0 = Any
        type B = A
        given Type[A0] = inType.asInstanceOf[Type[A0]]
        given Type[B] = outType.asInstanceOf[Type[B]]

        // Enriche the inner stream, already extracted during the optimization phase, gathers exitPredicates + local predicates to the inner
        val (innerEnrichedTree, innerDecls, innerAccumulatedPredicates) = enrich[B](optimizedInnerTree.asInstanceOf[StreamTree[B]], exitPredicates)
        val allInnerDecls = declarations ++ innerDecls

        // Recurisvy Enrich the upstream upstream using the predicates from the level of the flatmap
        val (enrichedUpstream, upstreamDeclarations, fullExitPredicates) = enrich(upstream, exitPredicates)

        val enrichedFlatMap = EnrichedFlatMap[A0, B](
          upstream = enrichedUpstream.asInstanceOf[StreamTree[A0]],
          innerEnrichedTree,
          inType = inType.asInstanceOf[Type[A0]],
          outType = outType.asInstanceOf[Type[B]],
          elemSymbol,
          innerDeclarations = allInnerDecls,
          predicates = innerAccumulatedPredicates // Here we have both the predicated from an higher level, and the flatmap one
        )

        (enrichedFlatMap.asInstanceOf[StreamTree[A]], upstreamDeclarations, fullExitPredicates)
      }

    }

  }

}
