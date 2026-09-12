package fuse

import scala.quoted.*
import CollectionStrategy.*

final class OPGenerator[OPIR <: AnyOPIR](val opIr: OPIR, val compileCfg: CompileConfig) {
  val ir: opIr.streamIr.type = opIr.streamIr
  private given macroQuotes: ir.quotes.type = ir.quotes

  import ir.*
  import ir.quotes.reflect.*
  import ir.StreamTree.*
  import opIr.{quotes => _, *} // We must only use the quotes instance of ir
  import opIr.Op.*

  def generate[ELEM, Buf, OUT](
      optimizedStream: AstExt[ELEM, Buf, OUT]
  )(using elemType: Type[ELEM], bufType: Type[Buf], outType: Type[OUT]): Program[OUT] = {
    val decls = optimizedStream.declarations
    println(s"Numero di dichiarazioni: ${decls.size}")
    println(s"Has an early exit ${optimizedStream.collectionStrategy.ref.nonEmpty}")

    optimizedStream.collectionStrategy.collectionStrategy match {
      case ToArray()                               => generateToArrayAccumulator[ELEM](optimizedStream.asInstanceOf[AstExt[ELEM, Nothing, Array[ELEM]]]).asInstanceOf[Program[OUT]]
      case _: Summing[t]                           => generateSummingAccumulator[t](optimizedStream.asInstanceOf[AstExt[t, Nothing, t]])(using elemType.asInstanceOf[Type[t]])
      case WithCollector[ELEM, Buf, OUT](collExpr) => generateGenericAccumulator(optimizedStream, collExpr)
    }
  }

  def generateToArrayAccumulator[OUT: Type](optimizedStream: AstExt[OUT, ?, Array[OUT]]): Program[Array[OUT]] = {

    val generated: Program[Array[OUT]] = optimizedStream.cardinality match {
      // Pipeline 1:1 dimensione esatta, se non abbiamo outputCardinalityUpperBound c'è un errore nel codices
      case Cardinality.Exact(sizeExpr) if optimizedStream.hasAlignedIndexes => {
        val array = createConstant[Array[OUT]]("vec")
        // Codice per emissione: assegna all'indice corrente il valore
        val emit: Emit[OUT] = emitted => ArrayWrite(SymbolRef(array), emitted.sourceIndex.get, emitted.elem)
        val body = buildBody[OUT](optimizedStream.enrichedStream, emit, optimizedStream.collectionStrategy.ref)
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
        val body = buildBody[OUT](optimizedStream.enrichedStream, emit, optimizedStream.collectionStrategy.ref)
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
        val body = buildBody[OUT](optimizedStream.enrichedStream, emit, optimizedStream.collectionStrategy.ref)
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
        val body = buildBody[OUT](optimizedStream.enrichedStream, emit, optimizedStream.collectionStrategy.ref)

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
    val sumSymbol = createVariable[T]("sum")
    val emit: Emit[T] = emitted => AssignVal(sumSymbol, Add(sumSymbol, emitted.elem))

    val loopBody = buildBody[T](optimizedStream.enrichedStream, emit, optimizedStream.collectionStrategy.ref)

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

  def generateGenericAccumulator[A: Type, Buf: Type, R: Type](optimizedStream: AstExt[A, Buf, R], collector: Expr[Collector[A, Buf, R]]): Program[R] = {
    val collectorSymbol = createConstant[Collector[A, Buf, R]]("collector")
    val bufferSymbol = createConstant[Buf]("buffer")
    val collectorRef = SymbolRef[Collector[A, Buf, R]](collectorSymbol)
    val bufferRef = SymbolRef[Buf](bufferSymbol)

    val emit: Emit[A] = emitted => {
      val accumulate = CollectorAccumulate(collectorRef, bufferRef, emitted.elem)
      optimizedStream.collectionStrategy.earlyExitVar match {
        case Some(exitRef) => Op.If(accumulate, AssignVal(exitRef.asTerm.symbol, ConstantVal(false)))
        case None          => Compute(accumulate)
      }
    }

    val body = buildBody(optimizedStream.enrichedStream, emit, optimizedStream.collectionStrategy.ref)
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

  private def buildBody[OUT](tree: StreamTree[Phase.Enriched, OUT], emit: Emit[OUT], exitPredicates: List[Expr[Boolean]]): Op = {
    tree match {
      case source: EnrichedJListSource[OUT]             => buildJListSource(source, emit, exitPredicates)
      case source: EnrichedArraySource[OUT]             => buildArraySource(source, emit, exitPredicates)
      case source: JIterableSource[Phase.Enriched, OUT] => buildJIterableSource(source, emit, exitPredicates)
      case source: IterableSource[Phase.Enriched, OUT]  => buildIterableSource(source, emit, exitPredicates)
      case filter: Filter[Phase.Enriched, OUT]          => buildFilter(filter, emit, exitPredicates)
      case map: Map[Phase.Enriched, ?, OUT]             => buildMap(map, emit, exitPredicates)
      case slice: EnrichedSlice[OUT]                    => buildSlice(slice, emit, exitPredicates)
      case flatmap: EnrichedFlatMap[in, OUT]            => buildFlatMap(flatmap, emit, exitPredicates)
    }
  }

  private def buildFlatMap[IN, OUT](flatMap: EnrichedFlatMap[IN, OUT], emit: Emit[OUT], exitPredicates: List[Expr[Boolean]]): Op = {
    given Type[IN] = flatMap.inType
    given Type[OUT] = flatMap.outType

    val upstreamEmit: Emit[IN] = emitted => {
      val innerBody = buildBody(flatMap.innerTree, emit, flatMap.predicates)
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

    buildBody(flatMap.upstream, upstreamEmit, exitPredicates)
  }

  private def buildSlice[OUT](slice: EnrichedSlice[OUT], emit: Emit[OUT], exitPredicates: List[Expr[Boolean]]): Op = {
    val counterRef = slice.counterRef
    val upstreamEmit: Emit[OUT] = emitted => {
      val output = slice.from match {
        case Some(from) => Op.If(GreaterThanOrEqual(ScalaExpr(counterRef), ScalaExpr(from)), emit(emitted))
        case None       => emit(emitted)
      }
      CodeBlock(List(output, Inc(counterRef.asTerm.symbol)))
    }

    buildBody(slice.upstream, upstreamEmit, exitPredicates)
  }

  private def buildArraySource[OUT](source: EnrichedArraySource[OUT], emit: Emit[OUT], earlyExitRef: List[Expr[Boolean]]): Op = {
    given Type[OUT] = source.outType

    val indexSymbol = createVariable[Int]("i")
    buildSourceLoop(
      LessThan(SymbolRef[Int](indexSymbol), ScalaExpr(source.sizeRef)),
      ArrayRead(source.term, indexSymbol),
      emit,
      earlyExitRef,
      Some(indexSymbol)
    )
  }

  private def buildIterableSource[OUT](source: IterableSource[Phase.Enriched, OUT], emit: Emit[OUT], exitPredicates: List[Expr[Boolean]]): Op = {
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

  private def buildJIterableSource[OUT](source: JIterableSource[Phase.Enriched, OUT], emit: Emit[OUT], exitPredicates: List[Expr[Boolean]]): Op = {
    given Type[OUT] = source.outType
    buildJIteratorSource(source.term, emit, exitPredicates)
  }

  private def buildJIteratorSource[OUT: Type](source: Expr[java.lang.Iterable[OUT]], emit: Emit[OUT], exitPredicates: List[Expr[Boolean]], indexed: Boolean = false): Op = {
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

  private def buildJListSource[OUT](source: EnrichedJListSource[OUT], emit: Emit[OUT], exitPredicates: List[Expr[Boolean]]): Op = {
    given Type[OUT] = source.outType
    val list = source.term

    Op.If(
      IsInstanceOf[java.util.ArrayList[?]](ScalaExpr(list)),
      buildArrayListSource(source, emit, exitPredicates),
      Some(buildJIteratorSource(list, emit, exitPredicates, indexed = true))
    )
  }

  private def buildArrayListSource[OUT](source: EnrichedJListSource[OUT], emit: Emit[OUT], exitPredicates: List[Expr[Boolean]]): Op = {
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
      indexSymbol: Option[Symbol] = None
  ): Op = {
    // Test early exits before hasNext, which may itself advance or evaluate a lazy source.
    val condition = exitPredicates.foldRight(sourceCondition)((predicate, rest) => And(ScalaExpr(predicate), rest))
    val elementSymbol = createConstant[OUT]("elem")
    val emitted = Emitted(SymbolRef[OUT](elementSymbol), indexSymbol.map(symbol => SymbolRef[Int](symbol)))

    // Materialize each element once so filters and collectors cannot repeat iterator.next().
    val loopBody = List(Declare(elementSymbol, nextElement), emit(emitted)) ++ indexSymbol.toList.map(Inc.apply)
    CodeBlock(
      indexSymbol.toList.map(symbol => Declare(symbol, ConstantVal(0))) ++ List(
        Op.While(
          condition,
          CodeBlock(loopBody)
        )
      )
    )
  }

  private def buildFilter[OUT](filter: Filter[Phase.Enriched, OUT], emit: Emit[OUT], earlyExitRef: List[Expr[Boolean]]): Op = {
    given Type[OUT] = filter.outType

    val upstreamEmit: Emit[OUT] = emitted => {
      val condition = ApplyFun(filter.predicate, emitted.elem)
      Op.If(condition, emit(emitted))
    }

    buildBody[OUT](filter.upstream, upstreamEmit, earlyExitRef)
  }

  private def buildMap[IN, OUT](map: Map[Phase.Enriched, IN, OUT], emit: Emit[OUT], earlyExitRef: List[Expr[Boolean]]): Op = {
    given Type[IN] = map.inType
    given Type[OUT] = map.outType
    println(s"Map function AST: ${map.function.show}")

    val upstreamEmit: Emit[IN] = u => {
      val mappedSymbol = createConstant[OUT]("mapped")
      // Downstream filters may use the mapped value both in their predicate and in their output.
      CodeBlock(List(Declare(mappedSymbol, ApplyFun(map.function, u.elem)), emit(Emitted(SymbolRef[OUT](mappedSymbol), u.sourceIndex))))
    }
    buildBody[IN](map.upstream, upstreamEmit, earlyExitRef)
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


}
