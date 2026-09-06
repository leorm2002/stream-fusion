package fuse

import scala.quoted.*
import fuse.FusedStream.*
import scala.collection.mutable.ArrayBuilder
final class CodeGenerator[IR <: AnyIR](val ir: IR) {
  private given macroQuotes: ir.quotes.type = ir.quotes

  import ir.*
  import ir.quotes.reflect.*

  // Due modalità di emissione, indicizzata e libera la prima da garanzia di allineamento e permette di usare un solo indice nel loop
  sealed trait Emit[A]

  object Emit {
    final case class Indexed[A](run: (Expr[A], Expr[Int]) => Expr[Unit]) extends Emit[A] {
      inline def apply(elem: Expr[A], index: Expr[Int]): Expr[Unit] = run(elem, index)
    }
    final case class Linear[A](run: Expr[A] => Expr[Unit]) extends Emit[A] {
      inline def apply(elem: Expr[A]): Expr[Unit] = run(elem)
    }
  }

  def generateCode[A: Type, Buf: Type, R: Type](optimizedStream: AstExt[A, Buf, R])(using Quotes): Expr[R] = {
    val decls = optimizedStream.declarations
    println(s"Numero di dichiarazioni: ${decls.size}")
    println(s"Has an early exit ${optimizedStream.collectionStrategy.ref.nonEmpty}")

    optimizedStream.collectionStrategy.collectionStrategy match {
      case ToArray()                          => generateToArrayAccumulator[A, Buf, R](optimizedStream)
      case WithCollector[A, Buf, R](collExpr) => generateGenericAccumulator[A, Buf, R](optimizedStream, collExpr)
    }
  }

  def generateToArrayAccumulator[A: Type, Buf: Type, R: Type](optimizedStream: AstExt[A, Buf, R])(using Quotes): Expr[R] = {
    val hasAlignedIndexes: Boolean = optimizedStream.hasAlignedIndexes
    val outputCardinalityUpperBound: Option[Expr[Int]] = optimizedStream.outputCardinalityUpperBound
    val decls = optimizedStream.declarations

    Type.of[A] match {
      // TODO: Specializzazione primitivi: evita boxing???

      case _ => {
        if (hasAlignedIndexes) {
          // Pipeline 1:1 dimensione esatta
          val sizeExpr: Expr[Int] = outputCardinalityUpperBound.getOrElse(report.errorAndAbort("Internal error: aligned indexes require a known source size"))

          '{
            val array = ${ newArray[A](sizeExpr) }

            ${
              val body = Emit.Indexed[A]((elem, srcIndex) => '{ array($srcIndex) = $elem })
              val loopBody = buildBody[A](optimizedStream.enrichedStream, body, optimizedStream.collectionStrategy.ref)

              Block(decls, loopBody.asTerm).asExprOf[Unit]
            }

            array.asInstanceOf[R]
          }
        } else {
          // Dimensione ridotta (es. Filter) o non definibile
          outputCardinalityUpperBound match {
            // Abbiamo un Upper Bound, preallochiamo al massimo e tronchiamo alla fine
            case Some(maxSizeExpr) =>
              '{
                val array = ${ newArray[A](maxSizeExpr) }
                var index = 0

                ${
                  val body = Emit.Linear[A](elem =>
                    '{
                      array(index) = $elem
                      index += 1
                    }
                  )

                  val loopBody = buildBody[A](optimizedStream.enrichedStream, body, optimizedStream.collectionStrategy.ref)

                  Block(decls, loopBody.asTerm).asExprOf[Unit]
                }

                // TODO:
                // if (index == array.length) {
                // } else {
                //   java.util.Arrays.copyOf(array, index).asInstanceOf[R]
                // }
                array.asInstanceOf[R]
              }

            // Dimensione completamente ignota buffer dinamico (TODO: parsare a versioni più performanti)
            case None =>
              '{
                val builder = new scala.collection.mutable.ArrayBuffer[A]()

                ${
                  val body = Emit.Linear[A](elem =>
                    '{
                      builder.addOne($elem)
                      ()
                    }
                  )

                  val loopBody = buildBody[A](optimizedStream.enrichedStream, body, optimizedStream.collectionStrategy.ref)
                  Block(decls, loopBody.asTerm).asExprOf[Unit]
                }

                val finalArray = ${ newArray[A]('{ builder.size }) }
                builder.copyToArray(finalArray)
                finalArray.asInstanceOf[R]
              }
          }
        }
      }
    }
  }

  /* Crea il codice che istanzia un array del tipo dato dal parametro di tipo e della size data
   */
  private def newArray[A: Type](size: Expr[Int])(using Quotes): Expr[Array[A]] = {
    val ctor = Select(New(TypeIdent(defn.ArrayClass)), defn.ArrayClass.primaryConstructor)
    val typedCtor = TypeApply(ctor, List(Inferred(TypeRepr.of[A])))
    Apply(typedCtor, List(size.asTerm)).asExprOf[Array[A]]
  }

  /** Given a list of predicates (expressionss which evaluates to boolean) returns an unique predicate who combines all the expressions */
  private def foldPredicates(predicates: List[Expr[Boolean]]): Option[Expr[Boolean]] = {
    predicates.reduceLeftOption((acc, pred) => '{ $acc && $pred })
  }
  def generateGenericAccumulator[A: Type, Buf: Type, R: Type](optimizedStream: AstExt[A, Buf, R], collector: Expr[Collector[A, Buf, R]])(using Quotes): Expr[R] = {
    val decls = optimizedStream.declarations
    println(s"Recieved ${optimizedStream.collectionStrategy.ref.size} predicates")
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
              val assignStmt = Assign(
                exitVarRef.asTerm,
                Literal(BooleanConstant(false))
              )
              val setExitFalse = assignStmt.asExprOf[Unit]

              '{
                val done = c.accumulator(buf, $result)
                if (done) {
                  $setExitFalse
                }
              }
            case None =>
              '{
                c.accumulator(buf, $result)
                ()
              }
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

  private def buildBody[A](tree: StreamTree[A], emit: Emit[A], exitPredicates: List[Expr[Boolean]])(using Quotes): Expr[Unit] = {
    tree match {
      case source: JListSource[A]          => buildJListSource(source, emit, exitPredicates)
      case source: JIterableSource[A]      => buildJIterableSource(source, emit, exitPredicates)
      case source: IterableSource[A]       => buildIterableSource(source, emit, exitPredicates)
      case source: ArraySource[A]          => buildArraySource(source, emit, exitPredicates)
      case filter: Filter[A]               => buildFilter(filter, emit, exitPredicates)
      case map: Map[?, ?]                  => buildMap(map, emit, exitPredicates)
      case slice: EnrichedSlice[A]         => buildSlice(slice, emit, exitPredicates)
      case flatmap: EnrichedFlatMap[a0, b] => buildFlatMap(flatmap, emit, exitPredicates)
      // Versioni "base" non devono arrivare qua
      case slice: Slice[A]           => report.errorAndAbort("Internal compiler error: non-enriched Skip reached CodeGenerator")
      case FlatMap(_, _, _, _, _, _) => report.errorAndAbort("Internal compiler error: non-enriched Skip reached CodeGenerator")
    }
  }
  private def buildFlatMap[A0, B](flatMap: EnrichedFlatMap[A0, B], emit: Emit[B], exitPredicates: List[Expr[Boolean]])(using Quotes): Expr[Unit] = {

    given Type[A0] = flatMap.inType
    given Type[B] = flatMap.outType

    checkForEmitType(emit, "flatMap")

    val flatMapEmit: Emit[A0] = Emit.Linear[A0](elemExpr => {
      // Cast the symbol to the current quotes instance
      val symbol = flatMap.elemSymbol

      // Bind the flatMapVariable to the value received from the previous computation
      val binderDeclaration = ValDef(symbol, Some(elemExpr.asTerm))

      // Generate the body of the flatmap, which emits to the producer
      val flatMapPredicated = flatMap.predicates
      val innerBody = buildBody[B](flatMap.innerTree, emit, flatMapPredicated)

      // Wrap in the iteration's local scope: [val outerElem = ..., var limitCounter = 0, <inner loop>]
      val allDeclarations = binderDeclaration :: flatMap.innerDeclarations
      Block(allDeclarations, innerBody.asTerm).asExprOf[Unit]
    })
    // Generate the body of the upstream, emitting into the flatmap
    buildBody[A0](flatMap.upstream, flatMapEmit, exitPredicates)
  }
  private def buildSlice[A](slice: EnrichedSlice[A], emit: Emit[A], earlyExitRef: List[Expr[Boolean]])(using Quotes): Expr[Unit] = {
    given Type[A] = slice.outType

    val callEmit: Expr[A] => Expr[Unit] = checkForEmitType(emit, "slice")
    val counterRef = slice.counterRef

    val upstreamEmit = Emit.Linear[A](elem => {
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

    buildBody[A](slice.upstream, upstreamEmit, earlyExitRef)
  }

  private def buildIterableSource[A](source: IterableSource[A], emit: Emit[A], earlyExitRef: List[Expr[Boolean]])(using Quotes): Expr[Unit] = {
    given Type[A] = source.outType
    println("Emitting while loop over an array source")
    val exitCond = foldPredicates(earlyExitRef)
    val callEmit: Expr[A] => Expr[Unit] = checkForEmitType(emit, "Scala iterable")
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
        val elem: A = iterator.next()
        ${ callEmit('elem) }
      }
    }
  }
  private def buildArraySource[A](source: ArraySource[A], emit: Emit[A], earlyExitRef: List[Expr[Boolean]])(using Quotes): Expr[Unit] = {
    given Type[A] = source.outType

    val exitCond = foldPredicates(earlyExitRef)
    '{
      val arr: Array[A] = ${ source.term }
      val len: Int = arr.length
      var i = 0
      // this get shifted with the match solved

      while (
        ${
          exitCond match {
            case Some(cond) => '{ i < len && ($cond) }
            case None       => '{ i < len }
          }
        }
      ) {
        ${
          emit match {
            case Emit.Indexed(f) => f('{ arr(i) }, '{ i })
            case Emit.Linear(f)  => f('{ arr(i) })
          }
        }
        i += 1
      }
    }
  }

  private def buildJIterableSource[A](source: JIterableSource[A], emit: Emit[A], earlyExitRef: List[Expr[Boolean]])(using Quotes): Expr[Unit] = {
    given Type[A] = source.outType
    val exitCond = foldPredicates(earlyExitRef)

    val callEmit: Expr[A] => Expr[Unit] = checkForEmitType(emit, "Java iterable")
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
        val elem: A = iterator.next()
        ${ callEmit('elem) }
      }
    }
  }

  private def buildJListSource[A](source: JListSource[A], emit: Emit[A], earlyExitRef: List[Expr[Boolean]])(using Quotes): Expr[Unit] = {

    given Type[A] = source.outType

    val exitCond = foldPredicates(earlyExitRef)

    val indexedEmit: (Expr[A], Expr[Int]) => Expr[Unit] = emit match {
      case Emit.Indexed(f) => f
      case Emit.Linear(f)  => (elem, _) => f(elem)
    }

    '{
      val list: java.util.List[A] = ${ source.term }

      list match {
        case arrayList: java.util.ArrayList[A @unchecked] =>
          val len = arrayList.size()
          var i = 0

          while (
            ${
              exitCond match {
                case Some(cond) => '{ i < len && $cond }
                case None       => '{ i < len }
              }
            }
          ) {
            ${
              indexedEmit('{ arrayList.get(i) }, '{ i })
            }

            i += 1
          }

        case _ =>
          val iterator = list.iterator()
          var i = 0

          while (
            ${
              exitCond match {
                case Some(cond) => '{ $cond && iterator.hasNext }
                case None       => '{ iterator.hasNext }
              }
            }
          ) {
            val elem: A = iterator.next()

            ${
              indexedEmit('{ elem }, '{ i })
            }

            i += 1
          }
      }
    }
  }

  private def buildFilter[A](filter: Filter[A], emit: Emit[A], earlyExitRef: List[Expr[Boolean]])(using Quotes): Expr[Unit] = {
    given Type[A] = filter.outType

    val upstreamEmit: Emit[A] = Emit.Linear[A](elem => {
      val cond = Expr.betaReduce('{ ${ filter.predicate }($elem) })
      emit match {
        case Emit.Linear(f)  => '{ if ($cond) { ${ f(elem) } } }
        case Emit.Indexed(_) => report.errorAndAbort("Internal error: Filter cannot emit to an Indexed consumer (pipeline should not have aligned indexes)")
      }
    })

    buildBody[A](filter.upstream, upstreamEmit, earlyExitRef)
  }

  private def buildMap[A, B](map: Map[A, B], emit: Emit[B], earlyExitRef: List[Expr[Boolean]])(using Quotes): Expr[Unit] = {
    given Type[A] = map.inType
    given Type[B] = map.outType
    println(s"Map function AST: ${map.function.show}")

    val upstreamEmit: Emit[A] = emit match {
      case Emit.Indexed(f) =>
        Emit.Indexed[A]((elem, idx) => {
          val mapped = Expr.betaReduce('{ ${ map.function }($elem) })
          f(mapped, idx)
        })
      case Emit.Linear(f) =>
        Emit.Linear[A](elem => {
          val mapped = Expr.betaReduce('{ ${ map.function }($elem) })
          f(mapped)
        })
    }

    buildBody[A](map.upstream, upstreamEmit, earlyExitRef)
  }

  private def checkForEmitType[A](emit: Emit[A], operator: String): Expr[A] => Expr[Unit] = {
    emit match {
      case Emit.Indexed(_) => report.errorAndAbort("Internal compiler error: FlatMap cannot emit to an Indexed consumer")
      case Emit.Linear(f)  => elem => f(elem)
    }

  }
}
