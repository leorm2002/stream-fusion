package fuse.internal

import scala.quoted.*
import CollectionStrategy.*
import fuse.internal.ir.Phase
import fuse.internal.ir.AnyOPIR
import fuse.CompileConfig
import fuse.Summable
import fuse.CollectorBase
import scala.compiletime.ops.int
import fuse.internal.ir.ExecutionMode

private final class OPGenerator[OPIR <: AnyOPIR](val opIr: OPIR, val compileCfg: CompileConfig, val logger: FusedLogger) {
  val ir: opIr.streamIr.type = opIr.streamIr
  private given macroQuotes: ir.quotes.type = ir.quotes

  import ir.*
  import ir.quotes.reflect.*
  import ir.StreamTree.*
  import opIr.{quotes => _, *} // We must only use the quotes instance of ir
  import opIr.Op.*
  import logger.*

  def generate[ELEM, Buf, OUT](optimizedStream: AstExt[ELEM, Buf, OUT])(using elemType: Type[ELEM], bufType: Type[Buf], outType: Type[OUT]): Program[OUT] = {
    val decls = optimizedStream.declarations
    debug(s"Numero di dichiarazioni: ${decls.size}")
    debug(s"Has an early exit ${optimizedStream.collectionStrategy.ref.nonEmpty}")
    val executionMode = optimizedStream.executionMode
    optimizedStream.collectionStrategy.collectionStrategy match {
      case ToArray()                  => generateToArrayAccumulator[ELEM](optimizedStream.asInstanceOf[AstExt[ELEM, Nothing, Array[ELEM]]]).asInstanceOf[Program[OUT]]
      case _: Summing[t]              => generateSummingAccumulator[t](optimizedStream.asInstanceOf[AstExt[t, Nothing, t]])(using elemType.asInstanceOf[Type[t]])
      case WithCollector(collExpr, _) => generateGenericAccumulator(optimizedStream, collExpr)
    }
  }

  def generateToArrayAccumulator[OUT: Type](optimizedStream: AstExt[OUT, ?, Array[OUT]]): Program[Array[OUT]] = {
    optimizedStream.executionMode match {
      case ExecutionMode.Sequential => generateSequentialToArrayAccumulator(optimizedStream)
      case ExecutionMode.Parallel   => generateParallelToArrayAccumulator(optimizedStream)
    }
  }

  def generateParallelToArrayAccumulator[OUT: Type](optimizedStream: AstExt[OUT, ?, Array[OUT]]): Program[Array[OUT]] = {
    optimizedStream.cardinality match {
      // Direct write nell'array finale
      case Cardinality.Exact(sizeExpr) if optimizedStream.hasAlignedIndexes => {
        // The array, it's only one shared across the threads
        val resultVec = createConstant[Array[OUT]]("outVec")
        val resultDeclare = Declare(resultVec, ArrayDefine[OUT](ScalaExpr(sizeExpr)))

        // We simlply write into the shared array on the source index since it's aligned
        val emit: Emit[OUT] = emitted => CodeBlock(List(ArrayWrite(SymbolRef(resultVec), emitted.sourceIndex.get, emitted.elem)))

        val fromSymbol = createConstant[Int]("from")
        val untilSymbol = createConstant[Int]("until")
        val range = Some(SourceRange(SymbolRef[Int](fromSymbol), SymbolRef[Int](untilSymbol)))
        val body = buildBody(optimizedStream.enrichedStream, emit, optimizedStream.collectionStrategy.ref, range)
        val declarations = getAllDeclarations(optimizedStream)
        val sourceSize = getParallelSourceSize(optimizedStream.enrichedStream).get

        val parallel = Parallel[Array[OUT]](
          returnSymbol = resultVec,
          collectionSize = ScalaExpr(sourceSize),
          from = fromSymbol,
          to = untilSymbol,
          statements = List(body),
          localResult = null, // Questo non esiste, niente accumulazione locale
          combiner = ParallelCombine.ArrayDirect[OUT]()
        )

        Program(statements = declarations ++ List(resultDeclare, parallel), result = SymbolRef[Array[OUT]](resultVec))
      }

      // Buffer locale fixed-size per chunk + count + merge
      case Cardinality.UpperBound(sizeExpr) => {
        val localVec = createConstant[Array[OUT]]("localVec")
        val i = createVariable[Int]("i")
        val resultSymbol = createConstant[Array[OUT]]("outVec")

        val emit: Emit[OUT] = emitted =>
          CodeBlock(
            List(
              ArrayWrite(SymbolRef(localVec), SymbolRef(i), emitted.elem), // write elem
              Inc(i) // increment the counter
            )
          )

        val fromSymbol = createConstant[Int]("from")
        val untilSymbol = createConstant[Int]("until")

        // Dichiaro l'array con TODO: size uguale al range
        val localSize = Subtract(SymbolRef[Int](untilSymbol), SymbolRef[Int](fromSymbol))
        val arrayDeclare = Declare(localVec, ArrayDefine[OUT](localSize)) // Declare the array

        val range = Some(SourceRange(SymbolRef[Int](fromSymbol), SymbolRef[Int](untilSymbol)))
        val body = buildBody(optimizedStream.enrichedStream, emit, optimizedStream.collectionStrategy.ref, range)
        val declarations = getAllDeclarations(optimizedStream)
        val sourceSize = getParallelSourceSize(optimizedStream.enrichedStream).get

        val parallel = Parallel[Array[OUT]](
          returnSymbol = resultSymbol,
          collectionSize = ScalaExpr(sourceSize),
          from = fromSymbol,
          to = untilSymbol,
          statements = List(arrayDeclare, Declare(i, ConstantVal(0)), body),
          localResult = SymbolRef[Array[OUT]](localVec),
          combiner = ParallelCombine.ArrayConcat[OUT](Some(SymbolRef(i)))
        )

        Program(statements = declarations ++ List(parallel), result = SymbolRef[Array[OUT]](resultSymbol))
      }

      // Dynamic buffer per chunk + merge
      case Cardinality.Unknown => {
        val localBuilder = createConstant[DynamicArrayBuilder[OUT]]("localBuilder")
        val localBuilderRef = SymbolRef[DynamicArrayBuilder[OUT]](localBuilder)
        val resultSymbol = createConstant[Array[OUT]]("outVec")
        val emit: Emit[OUT] = emitted => CodeBlock(List(DynamicArrayBuilderAdd(localBuilderRef, emitted.elem)))
        val fromSymbol = createConstant[Int]("from")
        val untilSymbol = createConstant[Int]("until")
        val arrayDeclare = Declare(localBuilder, DynamicArrayBuilderNew[OUT]())
        val range = Some(SourceRange(SymbolRef[Int](fromSymbol), SymbolRef[Int](untilSymbol)))
        val body = buildBody(optimizedStream.enrichedStream, emit, optimizedStream.collectionStrategy.ref, range)
        val declarations = getAllDeclarations(optimizedStream)
        val sourceSize = getParallelSourceSize(optimizedStream.enrichedStream).get
        val parallel = Parallel[Array[OUT]](
          returnSymbol = resultSymbol,
          collectionSize = ScalaExpr(sourceSize),
          from = fromSymbol,
          to = untilSymbol,
          statements = List(arrayDeclare, body),
          localResult = DynamicArrayBuilderResult(localBuilderRef),
          combiner = ParallelCombine.ArrayConcat[OUT](None)
        )
        Program(statements = declarations ++ List(parallel), result = SymbolRef[Array[OUT]](resultSymbol))
      }

      // Internal invariant violation
      case Cardinality.Exact(_) => report.errorAndAbort("Internal error: parallel stream with exact cardinality must have aligned indexes")
    }
  }
  def generateSequentialToArrayAccumulator[OUT: Type](optimizedStream: AstExt[OUT, ?, Array[OUT]]): Program[Array[OUT]] = {
    val generated: Program[Array[OUT]] = optimizedStream.cardinality match {
      // Pipeline 1:1 dimensione esatta, se non abbiamo outputCardinalityUpperBound c'è un errore nel codices
      case Cardinality.Exact(sizeExpr) if optimizedStream.hasAlignedIndexes => {
        val array = createConstant[Array[OUT]]("vec")
        // Codice per emissione: assegna all'indice corrente il valore
        val emit: Emit[OUT] = emitted => ArrayWrite(SymbolRef(array), emitted.sourceIndex.get, emitted.elem)
        val body = buildBody[OUT](optimizedStream.enrichedStream, emit, optimizedStream.collectionStrategy.ref, None)
        Program(
          List(
            Declare(array, ArrayDefine[OUT](ScalaExpr(sizeExpr))), // Declare the array
            body // Body of the stream
          ),
          SymbolRef(array) // Return the array
        )
      }
      case Cardinality.Exact(sizeExpr) => {
        val array = createConstant[Array[OUT]]("vec")
        val i = createVariable[Int]("i")

        val emit: Emit[OUT] = emitted =>
          CodeBlock(
            List(
              ArrayWrite(SymbolRef(array), SymbolRef(i), emitted.elem), // write elem
              Inc(i)
            )
          ) // increment the counter
        val body = buildBody[OUT](optimizedStream.enrichedStream, emit, optimizedStream.collectionStrategy.ref, None)
        Program(
          List(
            Declare(array, ArrayDefine[OUT](ScalaExpr(sizeExpr))), // Declare the array
            Declare(i, ConstantVal(0)), // Declare the counter
            body // The build body
          ),
          SymbolRef(array) // Return the array
        )
      }
      // Dimensione ridotta (es. Filter) o non definibile
      case Cardinality.UpperBound(sizeExpr) => {
        val array = createConstant[Array[OUT]]("vec")
        val i = createVariable[Int]("i")
        val emit: Emit[OUT] = emitted =>
          CodeBlock(
            List(
              ArrayWrite(SymbolRef(array), SymbolRef(i), emitted.elem), // write elem
              Inc(i)
            )
          ) // increment the counter
        val body = buildBody[OUT](optimizedStream.enrichedStream, emit, optimizedStream.collectionStrategy.ref, None)
        val arrayRef = SymbolRef[Array[OUT]](array)
        val indexRef = SymbolRef[Int](i)
        val result = IfValue(
          Equal(indexRef, ArrayLength(arrayRef)),
          arrayRef,
          ArrayTake(arrayRef, indexRef)
        )
        Program(
          List(
            Declare(array, ArrayDefine[OUT](ScalaExpr(sizeExpr))), // Declare the array
            Declare(i, ConstantVal(0)), // Declare the counter
            body // The build body
          ),
          result
        )

      }
      // Unknown cardinality uses the dynamic builder operations; its implementation belongs to code generation.
      case Cardinality.Unknown => {
        val builder = createConstant[DynamicArrayBuilder[OUT]]("builder")
        val builderRef = SymbolRef[DynamicArrayBuilder[OUT]](builder)
        val emit: Emit[OUT] = emitted => DynamicArrayBuilderAdd(builderRef, emitted.elem)
        val body = buildBody[OUT](optimizedStream.enrichedStream, emit, optimizedStream.collectionStrategy.ref, None)

        Program(
          List(
            Declare(builder, DynamicArrayBuilderNew[OUT]()),
            body
          ),
          DynamicArrayBuilderResult(builderRef)
        )
      }
    }

    val declarations = getAllDeclarations(optimizedStream)
    generated.copy(statements = declarations ++ generated.statements)

  }
  def generateSummingAccumulator[OUT <: Summable: Type](optimizedStream: AstExt[OUT, ?, OUT]): Program[OUT] = {
    Type.of[OUT] match {
      case '[Int]    => buildSum[Int](optimizedStream.asInstanceOf[AstExt[Int, ?, Int]], ConstantVal(0)).asInstanceOf[Program[OUT]]
      case '[Double] => buildSum[Double](optimizedStream.asInstanceOf[AstExt[Double, ?, Double]], ConstantVal(0d)).asInstanceOf[Program[OUT]]
      case '[Float]  => buildSum[Float](optimizedStream.asInstanceOf[AstExt[Float, ?, Float]], ConstantVal(0f)).asInstanceOf[Program[OUT]]
      case '[Long]   => buildSum[Long](optimizedStream.asInstanceOf[AstExt[Long, ?, Long]], ConstantVal(0L)).asInstanceOf[Program[OUT]]
    }
  }

  private def buildSum[T <: Summable: Type](optimizedStream: AstExt[T, ?, T], zero: Value[T]): Program[T] = {
    optimizedStream.executionMode match {
      case ExecutionMode.Sequential => {
        val sumSymbol = createVariable[T]("sum")
        val emit: Emit[T] = emitted => AssignVal(sumSymbol, Add(sumSymbol, emitted.elem))
        val loopBody = buildBody[T](optimizedStream.enrichedStream, emit, optimizedStream.collectionStrategy.ref, None)
        val declarations = getAllDeclarations(optimizedStream)

        Program(
          declarations ++ // All the declaration
            List(
              Declare(sumSymbol, zero), // Define the accumulator
              loopBody // Add the body of the stream
            ),
          SymbolRef[T](sumSymbol)
        )
      }

      case fuse.internal.ir.ExecutionMode.Parallel => {
        val localSumSymbol = createVariable[T]("localSum")
        val resultSymbol = createConstant[T]("parallelSum")
        val fromSymbol = createConstant[Int]("from")
        val untilSymbol = createConstant[Int]("until")
        val emit: Emit[T] = emitted => AssignVal(localSumSymbol, Add(SymbolRef[T](localSumSymbol), emitted.elem))
        val range = Some(SourceRange(SymbolRef[Int](fromSymbol), SymbolRef[Int](untilSymbol)))
        val body = buildBody(optimizedStream.enrichedStream, emit, optimizedStream.collectionStrategy.ref, range)
        val declarations = getAllDeclarations(optimizedStream)
        val sourceSize = getParallelSourceSize(optimizedStream.enrichedStream).get
        val parallel = Parallel[T](
          returnSymbol = resultSymbol,
          collectionSize = ScalaExpr(sourceSize),
          from = fromSymbol,
          to = untilSymbol,
          statements = List(Declare(localSumSymbol, zero), body),
          localResult = SymbolRef[T](localSumSymbol),
          combiner = ParallelCombine.Sum(zero)
        )
        Program(statements = declarations ++ List(parallel), result = SymbolRef[T](resultSymbol))
      }
    }
  }

  def generateGenericAccumulator[A: Type, Buf: Type, R: Type](optimizedStream: AstExt[A, Buf, R], collector: Expr[CollectorBase[A, Buf, R]]): Program[R] = {
    val collectorSymbol = createConstant[CollectorBase[A, Buf, R]]("collector")
    val bufferSymbol = createConstant[Buf]("buffer")
    val collectorRef = SymbolRef[CollectorBase[A, Buf, R]](collectorSymbol)
    val bufferRef = SymbolRef[Buf](bufferSymbol)

    val emit: Emit[A] = emitted => {
      val accumulate = CollectorAccumulate(collectorRef, bufferRef, emitted.elem)
      optimizedStream.collectionStrategy.earlyExitVar match {
        case Some(exitRef) => Op.If(accumulate, AssignVal(exitRef.asTerm.symbol, ConstantVal(false)))
        case None          => Compute(accumulate)
      }
    }

    val body = buildBody(optimizedStream.enrichedStream, emit, optimizedStream.collectionStrategy.ref, None)
    val declarations = getAllDeclarations(optimizedStream)

    Program(
      statements = declarations ++ List(
        Declare(collectorSymbol, ScalaExpr(collector)), // Estrai il collector
        Declare(bufferSymbol, CollectorSupplier(collectorRef)) // Ottieni l'istanza del buffer
      )
        ++ List(body), // Corpo dello stream
      result = CollectorFinish(collectorRef, bufferRef)
    )
  }

  private def buildBody[OUT](tree: StreamTree[Phase.Enriched, OUT], emit: Emit[OUT], exitPredicates: List[Expr[Boolean]], range: Option[SourceRange]): Op = {
    tree match {
      case source: EnrichedJListSource[OUT]             => buildJListSource(source, emit, exitPredicates, range)
      case source: EnrichedArraySource[OUT]             => buildArraySource(source, emit, exitPredicates, range)
      case source: JIterableSource[Phase.Enriched, OUT] => buildJIterableSource(source, emit, exitPredicates, range)
      case source: IterableSource[Phase.Enriched, OUT]  => buildIterableSource(source, emit, exitPredicates, range)
      case filter: Filter[Phase.Enriched, OUT]          => buildFilter(filter, emit, exitPredicates, range)
      case map: Map[Phase.Enriched, ?, OUT]             => buildMap(map, emit, exitPredicates, range)
      case slice: EnrichedSlice[OUT]                    => buildSlice(slice, emit, exitPredicates, range)
      case flatmap: EnrichedFlatMap[in, OUT]            => buildFlatMap(flatmap, emit, exitPredicates, range)
    }
  }

  private def buildFlatMap[IN, OUT](flatMap: EnrichedFlatMap[IN, OUT], emit: Emit[OUT], exitPredicates: List[Expr[Boolean]], range: Option[SourceRange]): Op = {
    given Type[IN] = flatMap.inType
    given Type[OUT] = flatMap.outType

    val upstreamEmit: Emit[IN] = emitted => {
      val innerBody = buildBody(flatMap.innerTree, emit, flatMap.predicates, None)
      // Bind the outer element before initializing the inner source. Inner counters and declarations
      // belong to this iteration, while the inherited predicates can stop all enclosing loops.
      CodeBlock(
        List(
          Declare(flatMap.elemSymbol, emitted.elem)
        ) ++
          flatMap.innerDeclarations.map(ExternalStatement.apply) ++
          flatMap.innerMaterialized.map(materializedToOp) ++
          List(innerBody)
      )
    }

    buildBody(flatMap.upstream, upstreamEmit, exitPredicates, range)
  }

  private def buildSlice[OUT](slice: EnrichedSlice[OUT], emit: Emit[OUT], exitPredicates: List[Expr[Boolean]], range: Option[SourceRange]): Op = {
    val counterRef = slice.counterRef
    val upstreamEmit: Emit[OUT] = emitted => {
      val output = slice.from match {
        case Some(from) => Op.If(GreaterThanOrEqual(ScalaExpr(counterRef), ScalaExpr(from)), emit(emitted))
        case None       => emit(emitted)
      }
      CodeBlock(List(output, Inc(counterRef.asTerm.symbol)))
    }

    buildBody(slice.upstream, upstreamEmit, exitPredicates, range)
  }

  private def buildArraySource[OUT](source: EnrichedArraySource[OUT], emit: Emit[OUT], earlyExitRef: List[Expr[Boolean]], range: Option[SourceRange]): Op = {
    given Type[OUT] = source.outType

    val indexSymbol = createVariable[Int]("i")

    val start = range.map(_.from).getOrElse(ConstantVal(0))
    val end = range.map(_.until).getOrElse(ScalaExpr(source.sizeRef))

    buildSourceLoop(
      LessThan(SymbolRef[Int](indexSymbol), end),
      ArrayRead(source.term, indexSymbol),
      emit,
      earlyExitRef,
      Some(indexSymbol),
      start
    )
  }

  private def buildIterableSource[OUT](source: IterableSource[Phase.Enriched, OUT], emit: Emit[OUT], exitPredicates: List[Expr[Boolean]], range: Option[SourceRange]): Op = {
    given Type[OUT] = source.outType
    val iteratorSymbol = createConstant[Iterator[OUT]]("iterator")
    val iteratorRef = SymbolRef[Iterator[OUT]](iteratorSymbol)

    CodeBlock(
      List(
        Declare(iteratorSymbol, IteratorOf(ScalaExpr(source.term))),
        buildSourceLoop(HasNext(iteratorRef), Next(iteratorRef), emit, exitPredicates)
      )
    )
  }

  private def buildJIterableSource[OUT](source: JIterableSource[Phase.Enriched, OUT], emit: Emit[OUT], exitPredicates: List[Expr[Boolean]], range: Option[SourceRange]): Op = {
    given Type[OUT] = source.outType
    buildJIteratorSource(source.term, emit, exitPredicates, false, range)
  }

  private def buildJIteratorSource[OUT: Type](
      source: Expr[java.lang.Iterable[OUT]],
      emit: Emit[OUT],
      exitPredicates: List[Expr[Boolean]],
      indexed: Boolean = false,
      range: Option[SourceRange]
  ): Op = {
    val iteratorSymbol = createConstant[java.util.Iterator[OUT]]("iterator")
    val iteratorRef = SymbolRef[java.util.Iterator[OUT]](iteratorSymbol)
    val indexSymbol = Option.when(indexed)(createVariable[Int]("i"))

    CodeBlock(
      List(
        Declare(iteratorSymbol, JIteratorOf(ScalaExpr(source))),
        buildSourceLoop(JHasNext(iteratorRef), JNext(iteratorRef), emit, exitPredicates, indexSymbol)
      )
    )
  }

  private def buildJListSource[OUT](source: EnrichedJListSource[OUT], emit: Emit[OUT], exitPredicates: List[Expr[Boolean]], range: Option[SourceRange]): Op = {
    given Type[OUT] = source.outType
    val list = source.term

    Op.If(
      IsInstanceOf[java.util.ArrayList[?]](ScalaExpr(list)),
      buildArrayListSource(source, emit, exitPredicates, range),
      Some(buildJIteratorSource(list, emit, exitPredicates, true, range))
    )
  }

  private def buildArrayListSource[OUT](source: EnrichedJListSource[OUT], emit: Emit[OUT], exitPredicates: List[Expr[Boolean]], range: Option[SourceRange]): Op = {
    given Type[OUT] = source.outType
    val list = ScalaExpr(source.term)
    val indexSymbol = createVariable[Int]("i")
    val indexRef = SymbolRef[Int](indexSymbol)
    val condition = LessThan(SymbolRef(indexSymbol), ScalaExpr(source.sizeRef))

    if (compileCfg.useUnsafe) {
      val rawSymbol = createConstant[Array[AnyRef]]("raw")
      val rawRef = SymbolRef[Array[AnyRef]](rawSymbol)
      CodeBlock(
        List(
          Declare(rawSymbol, ArrayListRawArray(list)),
          buildSourceLoop(condition, AsInstanceOf[OUT](ArrayRead(rawRef, indexRef)), emit, exitPredicates, Some(indexSymbol))
        )
      )
    } else {
      buildSourceLoop(condition, JListRead(list, indexRef), emit, exitPredicates, Some(indexSymbol))
    }
  }

  private def buildSourceLoop[OUT: Type](
      sourceCondition: Value[Boolean],
      nextElement: Value[OUT],
      emit: Emit[OUT],
      exitPredicates: List[Expr[Boolean]],
      indexSymbol: Option[Symbol] = None,
      from: Value[Int] = ConstantVal(0)
  ): Op = {
    // Test early exits before hasNext, which may itself advance or evaluate a lazy source.
    val condition = exitPredicates.foldRight(sourceCondition)((predicate, rest) => And(ScalaExpr(predicate), rest))
    val elementSymbol = createConstant[OUT]("elem")
    val emitted = Emitted(SymbolRef[OUT](elementSymbol), indexSymbol.map(symbol => SymbolRef[Int](symbol)))

    // Materialize each element once so filters and collectors cannot repeat iterator.next().
    val loopBody = List(Declare(elementSymbol, nextElement), emit(emitted)) ++ indexSymbol.toList.map(Inc.apply)
    CodeBlock(
      indexSymbol.toList.map(symbol => Declare(symbol, from)) ++ List(
        Op.While(
          condition,
          CodeBlock(loopBody)
        )
      )
    )
  }

  private def buildFilter[OUT](filter: Filter[Phase.Enriched, OUT], emit: Emit[OUT], earlyExitRef: List[Expr[Boolean]], range: Option[SourceRange]): Op = {
    given Type[OUT] = filter.outType

    val upstreamEmit: Emit[OUT] = emitted => {
      val condition = ApplyFun(filter.predicate, emitted.elem)
      Op.If(condition, emit(emitted))
    }

    buildBody[OUT](filter.upstream, upstreamEmit, earlyExitRef, range)
  }

  private def buildMap[IN, OUT](map: Map[Phase.Enriched, IN, OUT], emit: Emit[OUT], earlyExitRef: List[Expr[Boolean]], range: Option[SourceRange]): Op = {
    given Type[IN] = map.inType
    given Type[OUT] = map.outType
    debug(s"Map function AST: ${map.function.show}")

    val upstreamEmit: Emit[IN] = u => {
      val mappedSymbol = createConstant[OUT]("mapped")
      // Downstream filters may use the mapped value both in their predicate and in their output.
      CodeBlock(List(Declare(mappedSymbol, ApplyFun(map.function, u.elem)), emit(Emitted(SymbolRef[OUT](mappedSymbol), u.sourceIndex))))
    }
    buildBody[IN](map.upstream, upstreamEmit, earlyExitRef, range)
  }

  def getAllDeclarations[ELEM, Buf, OUT](optimizedStream: AstExt[ELEM, Buf, OUT]): List[Op] = {
    val prefixStatements = optimizedStream.prefixStatements.map(ExternalStatement.apply)
    val declarations = optimizedStream.declarations.map(materializedToOp)
    prefixStatements ++ declarations

  }

  private def materializedToOp(materialized: Declaration): Op =
    materialized match {
      case m: Declaration.Impl[t] =>
        given Type[t] = m.valueType
        Declare(m.symbol, ScalaExpr(m.expr))
    }

  // Value emitted during lowering together with optional
  // positional metadata provided by the current source.
  private type Emit[A] = Emitted[A] => Op
  private final case class Emitted[A](elem: Value[A], sourceIndex: Option[Value[Int]])

  private final case class SourceRange(
      from: Value[Int],
      until: Value[Int]
  )

  private def getParallelSourceSize(tree: StreamTree[Phase.Enriched, ?]): Option[Expr[Int]] = {
    tree match {
      case EnrichedJListSource(term, sizeRef, outType)                                                                         => Some(sizeRef)
      case EnrichedArraySource(term, sizeRef, outType)                                                                         => Some(sizeRef)
      case IterableSource(term, outType)                                                                                       => None
      case JIterableSource(term, outType)                                                                                      => None
      case Filter(upstream, predicate, outType)                                                                                => getParallelSourceSize(upstream)
      case Map(upstream, function, inType, outType)                                                                            => getParallelSourceSize(upstream)
      case EnrichedSlice(upstream, from, until, outType, counterRef)                                                           => None
      case EnrichedFlatMap(upstream, innerTree, inType, outType, elemSymbol, innerDeclarations, innerMaterialized, predicates) => getParallelSourceSize(upstream)
    }
  }
}
