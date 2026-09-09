package fuse

import scala.quoted.*
import fuse.FusedStream.*
import java.util.ArrayList
import scala.concurrent.ExecutionContext.Implicits.global
import CollectionStrategy.*

final class CodeGenerator[IR <: AnyIR](val ir: IR, val compileCfg: CompileConfig) {
  private given macroQuotes: ir.quotes.type = ir.quotes

  import ir.*
  import ir.quotes.reflect.*
  import ir.StreamTree.*

  // Value emitted during lowering together with optional
  // positional metadata provided by the current source.
  type Emit[A] = Emitted[A] => Expr[Unit]
  final case class Emitted[A](
      elem: Expr[A],
      sourceIndex: Option[Expr[Int]]
  )

  def generateCode[ELEM, Buf, OUT](
      optimizedStream: AstExt[ELEM, Buf, OUT]
  )(using elemType: Type[ELEM], bufType: Type[Buf], outType: Type[OUT], compileCfg: CompileConfig): Expr[OUT] = {
    val decls = optimizedStream.declarations
    println(s"Numero di dichiarazioni: ${decls.size}")
    println(s"Has an early exit ${optimizedStream.collectionStrategy.ref.nonEmpty}")

    optimizedStream.collectionStrategy.collectionStrategy match {
      case ToArray()                               => generateToArrayAccumulator[ELEM](optimizedStream.asInstanceOf[AstExt[ELEM, Nothing, Array[ELEM]]])
      case _: Summing[t]                           => generateSummingAccumulator[t](optimizedStream.asInstanceOf[AstExt[t, Nothing, t]])(using elemType.asInstanceOf[Type[t]])
      case WithCollector[ELEM, Buf, OUT](collExpr) => generateGenericAccumulator[ELEM, Buf, OUT](optimizedStream, collExpr)
    }
  }

  def generateSummingAccumulator[OUT <: Summable: Type](optimizedStream: AstExt[OUT, ?, OUT]): Expr[OUT] = {
    Type.of[OUT] match {
      case '[Int]    => buildSum[Int](optimizedStream.asInstanceOf[AstExt[Int, ?, Int]], 0).asExprOf[OUT]
      case '[Double] => buildSum[Double](optimizedStream.asInstanceOf[AstExt[Double, ?, Double]], 0d).asExprOf[OUT]
      case '[Float]  => buildSum[Float](optimizedStream.asInstanceOf[AstExt[Float, ?, Float]], 0f).asExprOf[OUT]
      case '[Long]   => buildSum[Long](optimizedStream.asInstanceOf[AstExt[Long, ?, Long]], 0L).asExprOf[OUT]
    }
  }

  private def buildSum[T <: Summable: Type](optimizedStream: AstExt[T, ?, T], zero: Summable): Expr[T] = {
    val sumSymbol = createVariable[T]("sum")
    val sumDef = createDef(sumSymbol, zero)
    // We can't write sumSymbol + elem.asTerm via quoted expression since the union type does not offer a common + operator
    // This is the easier way to implement it: bypass the checker and directly emit to the scala AST
    val emit: Emit[T] = emitted => Assign(Ref(sumSymbol), Select.overloaded(Ref(sumSymbol), "+", Nil, List(emitted.elem.asTerm))).asExprOf[Unit]
    val loopBody = buildBody[T](optimizedStream.enrichedStream, emit, optimizedStream.collectionStrategy.ref)
    Block(optimizedStream.declarations ++ List(sumDef, loopBody.asTerm), Ref(sumSymbol)).asExprOf[T]
  }

  def generateToArrayAccumulator[OUT: Type](optimizedStream: AstExt[OUT, ?, Array[OUT]]): Expr[Array[OUT]] = {
    val decls = optimizedStream.declarations

    val generated: Expr[Array[OUT]] = optimizedStream.cardinality match {
      // Pipeline 1:1 dimensione esatta, se non abbiamo outputCardinalityUpperBound c'è un errore nel codices
      case Cardinality.Exact(sizeExpr) if optimizedStream.hasAlignedIndexes => {
        '{
          val array = ${ newArray[OUT](sizeExpr) }
          ${
            // Codice per emissione: assegna all'indice corrente il valore
            val emit: Emit[OUT] = emitted => { '{ array(${ emitted.sourceIndex.get }) = ${ emitted.elem } } }
            buildBody[OUT](optimizedStream.enrichedStream, emit, optimizedStream.collectionStrategy.ref)
          }
          array
        }
      }
      case Cardinality.Exact(sizeExpr) => {
        '{
          val array = ${ newArray[OUT](sizeExpr) }
          var index = 0
          ${
            val emit: Emit[OUT] = emitted => '{ array(index) = ${ emitted.elem }; index += 1 }
            buildBody[OUT](optimizedStream.enrichedStream, emit, optimizedStream.collectionStrategy.ref)
          }
          array
        }
      }
      // Dimensione ridotta (es. Filter) o non definibile
      case Cardinality.UpperBound(sizeExpr) => {
        '{
          val array = ${ newArray[OUT](sizeExpr) }
          var index = 0
          ${
            val emit: Emit[OUT] = emitted => '{ array(index) = ${ emitted.elem }; index += 1 }
            buildBody[OUT](optimizedStream.enrichedStream, emit, optimizedStream.collectionStrategy.ref)
          }
          // Dobbiamo ritornare un sottoinsieme dell'array
          if (index == array.length)
            array
          else
            array.take(index)
        }
      }
      // Dimensione completamente ignota buffer dinamico (TODO: passare a versioni più performanti dell'arraybuffer)
      case Cardinality.Unknown => {
        // Type.of[OUT] match   TODO: Specializzazione primitivi: evita boxing???
        '{
          val builder = new scala.collection.mutable.ArrayBuffer[OUT]()
          ${
            val emit: Emit[OUT] = emitted => '{ builder.addOne(${ emitted.elem }); () }
            buildBody[OUT](optimizedStream.enrichedStream, emit, optimizedStream.collectionStrategy.ref)
          }
          val result = ${ newArray[OUT]('{ builder.size }) }
          builder.copyToArray(result)
          result
        }
      }
    }
    Block(decls, generated.asTerm).asExprOf[Array[OUT]]
  }

  /* Crea il codice che istanzia un array del tipo dato dal parametro di tipo e della size data
   */
  private def newArray[T: Type](size: Expr[Int]): Expr[Array[T]] = {
    val ctor = Select(New(TypeIdent(defn.ArrayClass)), defn.ArrayClass.primaryConstructor)
    val typedCtor = TypeApply(ctor, List(Inferred(TypeRepr.of[T])))
    Apply(typedCtor, List(size.asTerm)).asExprOf[Array[T]]
  }

  /** Given a list of predicates (expressionss which evaluates to boolean) returns an unique predicate who combines all the expressions */
  private def foldPredicates(predicates: List[Expr[Boolean]]): Option[Expr[Boolean]] = {
    predicates.reduceLeftOption((acc, pred) => '{ $acc && $pred })
  }

  def generateGenericAccumulator[A: Type, Buf: Type, R: Type](optimizedStream: AstExt[A, Buf, R], collector: Expr[Collector[A, Buf, R]]): Expr[R] = {
    val decls = optimizedStream.declarations
    val earlyExitRef: Option[Expr[Boolean]] = foldPredicates(optimizedStream.collectionStrategy.ref)

    // 2. Riferimento Singolo alla sola variabile mutabile di Early Exit (es: var keepProducing)
    // Se la strategia non ha early exit, questo è None!
    val earlyExitVarOpt: Option[Expr[Boolean]] = optimizedStream.collectionStrategy.earlyExitVar
    '{
      val c = $collector
      val buf = c.supplier()
      ${
        // Build the loop body with all the push operations. this is always not indexed since it'a generic user defined accumulator
        val push: Emit[A] = result => {
          earlyExitVarOpt match {
            case Some(exitVarRef) =>
              // Construct: keepProducing = false via TASTy Assign AST node
              // exitVarRef è GARANTITO essere un Ref a una var mutabile (es: keepProducing)
              val assignStmt = Assign(exitVarRef.asTerm, Literal(BooleanConstant(false)))
              val setExitFalse = assignStmt.asExprOf[Unit]

              '{
                val done = c.accumulator(buf, ${ result.elem })
                if (done) {
                  $setExitFalse
                }
              }
            case None => '{ c.accumulator(buf, ${ result.elem }); () }
          }
        }

        val loopBody = buildBody[A](optimizedStream.enrichedStream, push, earlyExitRef.toList)

        // wrap body in a block with the bubbled up val defs
        val loopTerm = loopBody.asTerm
        val blockTerm = Block(decls, loopTerm)

        blockTerm.asExprOf[Unit]
      }
      c.finisher(buf)
    }

  }

  private def buildBody[OUT](tree: StreamTree[Phase.Enriched, OUT], emit: Emit[OUT], exitPredicates: List[Expr[Boolean]]): Expr[Unit] = {
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
  private def buildFlatMap[IN, OUT](flatMap: EnrichedFlatMap[IN, OUT], emit: Emit[OUT], exitPredicates: List[Expr[Boolean]]): Expr[Unit] = {

    given Type[IN] = flatMap.inType
    given Type[OUT] = flatMap.outType

    val flatMapEmit: Emit[IN] = emitted => {
      // Cast the symbol to the current quotes instance
      val symbol = flatMap.elemSymbol

      // Bind the flatMapVariable to the value received from the previous computation
      val binderDeclaration = ValDef(symbol, Some(emitted.elem.asTerm))

      // Generate the body of the flatmap, which emits to the producer
      val flatMapPredicated = flatMap.predicates
      val innerBody = buildBody[OUT](flatMap.innerTree, emit, flatMapPredicated)

      // Wrap in the iteration's local scope: [val outerElem = ..., var limitCounter = 0, <inner loop>]
      val allDeclarations = binderDeclaration :: flatMap.innerDeclarations
      Block(allDeclarations, innerBody.asTerm).asExprOf[Unit]
    }
    // Generate the body of the upstream, emitting into the flatmap
    buildBody[IN](flatMap.upstream, flatMapEmit, exitPredicates)
  }

  private def buildSlice[OUT](slice: EnrichedSlice[OUT], emit: Emit[OUT], earlyExitRef: List[Expr[Boolean]]): Expr[Unit] = {
    given Type[OUT] = slice.outType

    val counterRef = slice.counterRef

    val upstreamEmit: Emit[OUT] = emitted => {
      val incrementTerm = Assign(counterRef.asTerm, '{ $counterRef + 1 }.asTerm)
      val incrementExpr = incrementTerm.asExprOf[Unit]

      slice.from match {
        case Some(from) =>
          '{
            if ($counterRef >= $from) {
              ${ emit(emitted) }
            }
            $incrementExpr
          }
        case None =>
          '{
            ${ emit(emitted) }
            $incrementExpr
          }
      }
    }

    buildBody[OUT](slice.upstream, upstreamEmit, earlyExitRef)
  }

  private def buildIterableSource[OUT](source: IterableSource[Phase.Enriched, OUT], emit: Emit[OUT], earlyExitRef: List[Expr[Boolean]]): Expr[Unit] = {
    given Type[OUT] = source.outType
    val exitCond = foldPredicates(earlyExitRef)
    '{
      val iterator = ${ source.term }.iterator
      // this get shifted with the match solved
      while (
        ${
          exitCond match {
            case Some(cond) => '{ $cond && iterator.hasNext }
            case None       => '{ iterator.hasNext }
          }
        }
      ) {
        val elem: OUT = iterator.next()
        ${ emit(Emitted('elem, None)) }
      }
    }
  }
  private def buildArraySource[OUT](source: EnrichedArraySource[OUT], emit: Emit[OUT], earlyExitRef: List[Expr[Boolean]]): Expr[Unit] = {
    given Type[OUT] = source.outType

    val exitCond = foldPredicates(earlyExitRef)
    val arr = source.term
    val len = source.sizeRef
    '{
      var i = 0
      // this get shifted with the match solved

      while (
        ${
          exitCond match {
            case Some(cond) => '{ i < $len && ($cond) }
            case None       => '{ i < $len }
          }
        }
      ) {
        ${
          emit(Emitted('{ $arr(i) }, Some('{ i })))
        }
        i += 1
      }
    }
  }

  private def buildJIterableSource[OUT](source: JIterableSource[Phase.Enriched, OUT], emit: Emit[OUT], earlyExitRef: List[Expr[Boolean]]): Expr[Unit] = {
    given Type[OUT] = source.outType
    val exitCond = foldPredicates(earlyExitRef)

    '{
      val iterator = ${ source.term }.iterator
      while (
        ${
          exitCond match {
            case Some(cond) => '{ $cond && iterator.hasNext }
            case None       => '{ iterator.hasNext }
          }
        }
      ) {
        val elem: OUT = iterator.next()
        ${ emit(Emitted('elem, None)) }
      }
    }
  }

  private def buildJListSource[OUT](source: EnrichedJListSource[OUT], emit: Emit[OUT], earlyExitRef: List[Expr[Boolean]]): Expr[Unit] = {

    given Type[OUT] = source.outType
    val exitCond = foldPredicates(earlyExitRef)

    val list = source.term
    val len = source.sizeRef
    '{
      if ($list.isInstanceOf[java.util.ArrayList[OUT @unchecked]]) {
        var i = 0
        // Estrazione dell'array sottostante per massima performance in accesso
        ${
          if (compileCfg.useUnsafe) {
            '{
              val raw = ArrayListAccessor.getRawArray($list.asInstanceOf[java.util.ArrayList[OUT]])
              while (
                ${
                  exitCond match {
                    case Some(cond) => '{ i < $len && $cond }
                    case None       => '{ i < $len }
                  }
                }
              ) {
                ${ emit(Emitted('{ raw(i).asInstanceOf[OUT] },Some( '{ i }))) }
                i += 1
              }

            }

          } else {
            '{
              while (
                ${
                  exitCond match {
                    case Some(cond) => '{ i < $len && $cond }
                    case None       => '{ i < $len }
                  }
                }
              ) {
                ${ emit(Emitted('{ $list.get(i).asInstanceOf[OUT] }, Some('{ i }))) }
                i += 1
              }

            }
          }
        }
      } else {

        val iterator = $list.iterator()
        var i = 0
        while (
          ${
            exitCond match {
              case Some(cond) => '{ $cond && iterator.hasNext }
              case None       => '{ iterator.hasNext }
            }
          }
        ) {
          ${ emit(Emitted('{ iterator.next() }, Some('{ i }))) }
          i += 1
        }
      }
    }
  }

  private def buildFilter[OUT](filter: Filter[Phase.Enriched, OUT], emit: Emit[OUT], earlyExitRef: List[Expr[Boolean]]): Expr[Unit] = {
    given Type[OUT] = filter.outType

    val upstreamEmit: Emit[OUT] = u => {
      val cond = Expr.betaReduce('{ ${ filter.predicate }(${ u.elem }) })
      '{
        if ($cond) {
          ${ emit(u) }
        }
      }
    }

    buildBody[OUT](filter.upstream, upstreamEmit, earlyExitRef)
  }

  private def buildMap[IN, OUT](map: Map[Phase.Enriched, IN, OUT], emit: Emit[OUT], earlyExitRef: List[Expr[Boolean]]): Expr[Unit] = {
    given Type[IN] = map.inType
    given Type[OUT] = map.outType
    println(s"Map function AST: ${map.function.show}")

    val upstreamEmit: Emit[IN] = u => {
      val mapped = Expr.betaReduce('{ ${ map.function }(${ u.elem }) })
      emit(Emitted(mapped, u.sourceIndex))
    }
    buildBody[IN](map.upstream, upstreamEmit, earlyExitRef)
  }

}
