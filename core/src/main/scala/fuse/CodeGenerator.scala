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

  // Due modalità di emissione, indicizzata e libera la prima da garanzia di allineamento e permette di usare un solo indice nel loop
  sealed trait Emit[A]

  object Emit {
    final case class Indexed[OUT](run: (Expr[OUT], Expr[Int]) => Expr[Unit]) extends Emit[OUT] {
      inline def apply(elem: Expr[OUT], index: Expr[Int]): Expr[Unit] = run(elem, index)
    }
    final case class Linear[OUT](run: Expr[OUT] => Expr[Unit]) extends Emit[OUT] {
      inline def apply(elem: Expr[OUT]): Expr[Unit] = run(elem)
    }
  }

  def generateCode[ELEM, Buf, OUT](optimizedStream: AstExt[ELEM, Buf, OUT])(using elemType: Type[ELEM], bufType: Type[Buf], outType: Type[OUT], q: Quotes, compileCfg: CompileConfig): Expr[OUT] = {
    val decls = optimizedStream.declarations
    println(s"Numero di dichiarazioni: ${decls.size}")
    println(s"Has an early exit ${optimizedStream.collectionStrategy.ref.nonEmpty}")

    optimizedStream.collectionStrategy.collectionStrategy match {
      case ToArray()                               => generateToArrayAccumulator[ELEM](optimizedStream.asInstanceOf[AstExt[ELEM, Nothing, Array[ELEM]]])
      case Summing()                               => generateSummingAccumulator[ELEM](optimizedStream.asInstanceOf[AstExt[ELEM, Nothing, ELEM]])(using elemType)
      case WithCollector[ELEM, Buf, OUT](collExpr) => generateGenericAccumulator[ELEM, Buf, OUT](optimizedStream, collExpr)
    }
  }
  def generateSummingAccumulator[OUT: Type](optimizedStream: AstExt[OUT, ?, OUT])(using Quotes): Expr[OUT] = {
    val (a, b) =
      Type.of[OUT] match {

        case '[Int] => {
          val sumSymbol = createVariable[Int]("sum")
          val sumDef = createDef[Int](sumSymbol, 0)

          val body = Emit.Linear[Int](elem => {
            val currentSum = Ref(sumSymbol).asExprOf[Int]
            Assign(Ref(sumSymbol), '{ $currentSum + $elem }.asTerm).asExprOf[Unit]
          })
          val stream = optimizedStream.enrichedStream.asInstanceOf[StreamTree[Int]]

          val loopBody = buildBody[Int](stream, body, optimizedStream.collectionStrategy.ref)
          (List(sumDef, loopBody.asTerm), Ref(sumSymbol))
        }
        case '[Double] => {
          val sumSymbol = createVariable[Double]("sum")
          val sumDef = createDef[Double](sumSymbol, 0)

          val body = Emit.Linear[Double](elem => {
            val currentSum = Ref(sumSymbol).asExprOf[Double]
            Assign(Ref(sumSymbol), '{ $currentSum + $elem }.asTerm).asExprOf[Unit]
          })
          val stream = optimizedStream.enrichedStream.asInstanceOf[StreamTree[Double]]

          val loopBody = buildBody[Double](stream, body, optimizedStream.collectionStrategy.ref)
          (List(sumDef, loopBody.asTerm), Ref(sumSymbol))
        }
        case '[Float] => {
          val sumSymbol = createVariable[Float]("sum")
          val sumDef = createDef[Float](sumSymbol, 0)

          val body = Emit.Linear[Float](elem => {
            val currentSum = Ref(sumSymbol).asExprOf[Float]
            Assign(Ref(sumSymbol), '{ $currentSum + $elem }.asTerm).asExprOf[Unit]
          })
          val stream = optimizedStream.enrichedStream.asInstanceOf[StreamTree[Float]]

          val loopBody = buildBody[Float](stream, body, optimizedStream.collectionStrategy.ref)
          (List(sumDef, loopBody.asTerm), Ref(sumSymbol))
        }
        case '[Long] => {
          val sumSymbol = createVariable[Long]("sum")
          val sumDef = createDef[Long](sumSymbol, 0)

          val body = Emit.Linear[Long](elem => {
            val currentSum = Ref(sumSymbol).asExprOf[Long]
            Assign(Ref(sumSymbol), '{ $currentSum + $elem }.asTerm).asExprOf[Unit]
          })
          val stream = optimizedStream.enrichedStream.asInstanceOf[StreamTree[Long]]

          val loopBody = buildBody[Long](stream, body, optimizedStream.collectionStrategy.ref)
          (List(sumDef, loopBody.asTerm), Ref(sumSymbol))
        }

        case _ => quotes.reflect.report.errorAndAbort(s"Collector.summing is not supported for ${Type.show[OUT]}")
      }

    Block(optimizedStream.declarations ++ a, b).asExprOf[OUT]
  }

  def generateToArrayAccumulator[OUT: Type](optimizedStream: AstExt[OUT, ?, Array[OUT]])(using Quotes): Expr[Array[OUT]] = {
    val hasAlignedIndexes: Boolean = optimizedStream.hasAlignedIndexes
    val sizeRef = getSourceSizeRef(optimizedStream.enrichedStream)
    val decls = optimizedStream.declarations
    val hasEarlyExit = optimizedStream.collectionStrategy.ref.nonEmpty
    val generated: Expr[Array[OUT]] = Type.of[OUT] match {
      // TODO: Specializzazione primitivi: evita boxing???
      case _ => {
        if (hasAlignedIndexes && !hasEarlyExit) {
          // Pipeline 1:1 dimensione esatta, se non abbiamo outputCardinalityUpperBound c'è un errore nel codices
          val sizeExpr: Expr[Int] = sizeRef.getOrElse(report.errorAndAbort("Internal error: aligned indexes require a known source size"))

          '{
            val array = ${ newArray[OUT](sizeExpr) }
            ${
              // Codice per emissione: assegna all'indice corrente il valore
              val body = Emit.Indexed[OUT]((elem, srcIndex) => '{ array($srcIndex) = $elem })
              buildBody[OUT](optimizedStream.enrichedStream, body, optimizedStream.collectionStrategy.ref)
            }

            array
          }
        } else {
          // Dimensione ridotta (es. Filter) o non definibile
          sizeRef match {
            // Abbiamo un Upper Bound, preallochiamo al massimo e tronchiamo alla fine
            case Some(maxSizeExpr) =>
              '{
                val array = ${ newArray[OUT](maxSizeExpr) }
                var index = 0

                ${
                  val emit = Emit.Linear[OUT](elem => '{ array(index) = $elem; index += 1 })
                  buildBody[OUT](optimizedStream.enrichedStream, emit, optimizedStream.collectionStrategy.ref)
                }
                // Dobbiamo ritornare un sottoinsieme dell'array
                if (index == array.length)
                  array
                else
                  array.take(index)
              }

            // Dimensione completamente ignota buffer dinamico (TODO: passare a versioni più performanti dell'arraybuffer)
            case None =>
              '{
                val builder = new scala.collection.mutable.ArrayBuffer[OUT]()

                ${
                  val emit = Emit.Linear[OUT](elem => '{ builder.addOne($elem); () })
                  buildBody[OUT](optimizedStream.enrichedStream, emit, optimizedStream.collectionStrategy.ref)
                }

                val result = ${ newArray[OUT]('{ builder.size }) }
                builder.copyToArray(result)
                result
              }
          }
        }
      }
    }
    Block(decls, generated.asTerm).asExprOf[Array[OUT]]
  }

  /* Crea il codice che istanzia un array del tipo dato dal parametro di tipo e della size data
   */
  private def newArray[T: Type](size: Expr[Int])(using Quotes): Expr[Array[T]] = {
    val ctor = Select(New(TypeIdent(defn.ArrayClass)), defn.ArrayClass.primaryConstructor)
    val typedCtor = TypeApply(ctor, List(Inferred(TypeRepr.of[T])))
    Apply(typedCtor, List(size.asTerm)).asExprOf[Array[T]]
  }

  /** Given a list of predicates (expressionss which evaluates to boolean) returns an unique predicate who combines all the expressions */
  private def foldPredicates(predicates: List[Expr[Boolean]]): Option[Expr[Boolean]] = {
    predicates.reduceLeftOption((acc, pred) => '{ $acc && $pred })
  }

  def generateGenericAccumulator[A: Type, Buf: Type, R: Type](optimizedStream: AstExt[A, Buf, R], collector: Expr[Collector[A, Buf, R]])(using Quotes): Expr[R] = {
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
        val push: Emit[A] = Emit.Linear[A](result => {
          earlyExitVarOpt match {
            case Some(exitVarRef) =>
              // Construct: keepProducing = false via TASTy Assign AST node
              // exitVarRef è GARANTITO essere un Ref a una var mutabile (es: keepProducing)
              val assignStmt = Assign(exitVarRef.asTerm, Literal(BooleanConstant(false)))
              val setExitFalse = assignStmt.asExprOf[Unit]

              '{
                val done = c.accumulator(buf, $result)
                if (done) {
                  $setExitFalse
                }
              }
            case None => '{ c.accumulator(buf, $result); () }
          }
        })

        val loopBody = buildBody[A](optimizedStream.enrichedStream, push, earlyExitRef.toList)

        // wrap body in a block with the bubbled up val defs
        val loopTerm = loopBody.asTerm
        val blockTerm = Block(decls, loopTerm)

        blockTerm.asExprOf[Unit]
      }
      c.finisher(buf)
    }

  }

  private def buildBody[OUT](tree: StreamTree[OUT], emit: Emit[OUT], exitPredicates: List[Expr[Boolean]])(using Quotes): Expr[Unit] = {
    tree match {
      case source: EnrichedJListSource[OUT]  => buildJListSource(source, emit, exitPredicates)
      case source: EnrichedArraySource[OUT]  => buildArraySource(source, emit, exitPredicates)
      case source: JIterableSource[OUT]      => buildJIterableSource(source, emit, exitPredicates)
      case source: IterableSource[OUT]       => buildIterableSource(source, emit, exitPredicates)
      case filter: Filter[OUT]               => buildFilter(filter, emit, exitPredicates)
      case map: Map[?, OUT]                  => buildMap(map, emit, exitPredicates)
      case slice: EnrichedSlice[OUT]         => buildSlice(slice, emit, exitPredicates)
      case flatmap: EnrichedFlatMap[in, OUT] => buildFlatMap(flatmap, emit, exitPredicates)
      // Versioni "base" non devono arrivare qua
      case source: JListSource[OUT] => report.errorAndAbort("Internal compiler error: non-enriched Slice reached CodeGenerator")
      case source: ArraySource[OUT] => report.errorAndAbort("Internal compiler error: non-enriched Slice reached CodeGenerator")

      case slice: Slice[OUT]      => report.errorAndAbort("Internal compiler error: non-enriched Slice reached CodeGenerator")
      case flatMap: FlatMap[_, _] => report.errorAndAbort("Internal compiler error: non-enriched FlatMap reached CodeGenerator")
    }
  }
  private def buildFlatMap[IN, OUT](flatMap: EnrichedFlatMap[IN, OUT], emit: Emit[OUT], exitPredicates: List[Expr[Boolean]])(using Quotes): Expr[Unit] = {

    given Type[IN] = flatMap.inType
    given Type[OUT] = flatMap.outType

    checkForEmitType(emit, "flatMap")

    val flatMapEmit: Emit[IN] = Emit.Linear[IN](elemExpr => {
      // Cast the symbol to the current quotes instance
      val symbol = flatMap.elemSymbol

      // Bind the flatMapVariable to the value received from the previous computation
      val binderDeclaration = ValDef(symbol, Some(elemExpr.asTerm))

      // Generate the body of the flatmap, which emits to the producer
      val flatMapPredicated = flatMap.predicates
      val innerBody = buildBody[OUT](flatMap.innerTree, emit, flatMapPredicated)

      // Wrap in the iteration's local scope: [val outerElem = ..., var limitCounter = 0, <inner loop>]
      val allDeclarations = binderDeclaration :: flatMap.innerDeclarations
      Block(allDeclarations, innerBody.asTerm).asExprOf[Unit]
    })
    // Generate the body of the upstream, emitting into the flatmap
    buildBody[IN](flatMap.upstream, flatMapEmit, exitPredicates)
  }
  private def buildSlice[OUT](slice: EnrichedSlice[OUT], emit: Emit[OUT], earlyExitRef: List[Expr[Boolean]])(using Quotes): Expr[Unit] = {
    given Type[OUT] = slice.outType

    val callEmit: Expr[OUT] => Expr[Unit] = checkForEmitType(emit, "slice")
    val counterRef = slice.counterRef

    val upstreamEmit = Emit.Linear[OUT](elem => {
      val incrementTerm = Assign(counterRef.asTerm, '{ $counterRef + 1 }.asTerm)
      val incrementExpr = incrementTerm.asExprOf[Unit]

      slice.from match {
        case Some(from) =>
          '{
            if ($counterRef >= $from) {
              ${ callEmit(elem) }
            }
            $incrementExpr
          }
        case None =>
          '{
            ${ callEmit(elem) }
            $incrementExpr
          }
      }
    })

    buildBody[OUT](slice.upstream, upstreamEmit, earlyExitRef)
  }

  private def buildIterableSource[OUT](source: IterableSource[OUT], emit: Emit[OUT], earlyExitRef: List[Expr[Boolean]])(using Quotes): Expr[Unit] = {
    given Type[OUT] = source.outType
    val exitCond = foldPredicates(earlyExitRef)
    val callEmit: Expr[OUT] => Expr[Unit] = checkForEmitType(emit, "Scala iterable")
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
        ${ callEmit('elem) }
      }
    }
  }
  private def buildArraySource[OUT](source: EnrichedArraySource[OUT], emit: Emit[OUT], earlyExitRef: List[Expr[Boolean]])(using Quotes): Expr[Unit] = {
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
          emit match {
            case Emit.Indexed(f) => f('{ $arr(i) }, '{ i })
            case Emit.Linear(f)  => f('{ $arr(i) })
          }
        }
        i += 1
      }
    }
  }

  private def buildJIterableSource[OUT](source: JIterableSource[OUT], emit: Emit[OUT], earlyExitRef: List[Expr[Boolean]])(using Quotes): Expr[Unit] = {
    given Type[OUT] = source.outType
    val exitCond = foldPredicates(earlyExitRef)

    val callEmit: Expr[OUT] => Expr[Unit] = checkForEmitType(emit, "Java iterable")
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
        ${ callEmit('elem) }
      }
    }
  }

  private def buildJListSource[OUT](source: EnrichedJListSource[OUT], emit: Emit[OUT], earlyExitRef: List[Expr[Boolean]])(using Quotes): Expr[Unit] = {

    given Type[OUT] = source.outType
    val exitCond = foldPredicates(earlyExitRef)
    val indexedEmit: (Expr[OUT], Expr[Int]) => Expr[Unit] = emit match {
      case Emit.Indexed(f) => f
      case Emit.Linear(f)  => (elem, _) => f(elem)
    }

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
                ${ indexedEmit('{ raw(i).asInstanceOf[OUT] }, '{ i }) }
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
                ${ indexedEmit('{ $list.get(i).asInstanceOf[OUT] }, '{ i }) }
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
          ${ indexedEmit('{ iterator.next() }, '{ i }) }
          i += 1
        }
      }
    }
  }

  private def buildFilter[OUT](filter: Filter[OUT], emit: Emit[OUT], earlyExitRef: List[Expr[Boolean]])(using Quotes): Expr[Unit] = {
    given Type[OUT] = filter.outType

    val upstreamEmit: Emit[OUT] = Emit.Linear[OUT](elem => {
      val cond = Expr.betaReduce('{ ${ filter.predicate }($elem) })
      emit match {
        case Emit.Linear(f)  => '{ if ($cond) { ${ f(elem) } } }
        case Emit.Indexed(_) => report.errorAndAbort("Internal error: Filter cannot emit to an Indexed consumer (pipeline should not have aligned indexes)")
      }
    })

    buildBody[OUT](filter.upstream, upstreamEmit, earlyExitRef)
  }

  private def buildMap[IN, OUT](map: Map[IN, OUT], emit: Emit[OUT], earlyExitRef: List[Expr[Boolean]])(using Quotes): Expr[Unit] = {
    given Type[IN] = map.inType
    given Type[OUT] = map.outType
    println(s"Map function AST: ${map.function.show}")

    val upstreamEmit: Emit[IN] = emit match {
      case Emit.Indexed(f) =>
        Emit.Indexed[IN]((elem, idx) => {
          val mapped = Expr.betaReduce('{ ${ map.function }($elem) })
          f(mapped, idx)
        })
      case Emit.Linear(f) =>
        Emit.Linear[IN](elem => {
          val mapped = Expr.betaReduce('{ ${ map.function }($elem) })
          f(mapped)
        })
    }

    buildBody[IN](map.upstream, upstreamEmit, earlyExitRef)
  }

  private def checkForEmitType[OUT](emit: Emit[OUT], operator: String): Expr[OUT] => Expr[Unit] = {
    emit match {
      case Emit.Indexed(_) => report.errorAndAbort(s"Internal compiler error: $operator cannot emit to an Indexed consumer")
      case Emit.Linear(f)  => elem => f(elem)
    }

  }

  private def getSourceSizeRef(tree: StreamTree[?]): Option[Expr[Int]] = tree match {
    case source: EnrichedArraySource[?] => Some(source.sizeRef)
    case source: EnrichedJListSource[?] => Some(source.sizeRef)
    case map: Map[?, ?]                 => getSourceSizeRef(map.upstream)
    case filter: Filter[?]              => getSourceSizeRef(filter.upstream)
    case slice: EnrichedSlice[?]        => getSourceSizeRef(slice.upstream)
    case _: EnrichedFlatMap[?, ?]       => None
    case _                              => None
  }

}
