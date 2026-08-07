package fuse

import scala.quoted.*
import fuse.FusedStream.*
import scala.collection.mutable.ArrayBuilder
final class CodeGenerator[IR <: AnyIR](val ir: IR) {
  private given macroQuotes: ir.quotes.type = ir.quotes

  import ir.*
  import ir.quotes.reflect.*

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
    // Need to cast is to avoid problems with quotes
    val decls = optimizedStream.declarations
    println(s"Numero di dichiarazioni: ${decls.size}")

    // Hand specilized compile time dispatch based on the type => no boxing
    Type.of[A] match {
      case '[Int] =>
        '{
          val builder = new scala.collection.mutable.ArrayBuilder.ofInt()

          ${
            val intBody: Expr[Int] => Expr[Unit] = elem => '{ builder.addOne($elem) }
            // This is safe since it's done at compile time
            val streamInt = optimizedStream.enrichedStream.asInstanceOf[StreamTree[Int]]
            val loopBody = buildBody[Int](streamInt, intBody, optimizedStream.collectionStrategy.ref)

            Block(decls, loopBody.asTerm).asExprOf[Unit]
          }

          builder.result().asInstanceOf[R]
        }

      case '[Double] =>
        '{
          val builder = new scala.collection.mutable.ArrayBuilder.ofDouble()

          ${
            val doubleBody: Expr[Double] => Expr[Unit] = elem => '{ builder.addOne($elem) }
            // This is safe since it's done at compile time
            val streamDouble = optimizedStream.enrichedStream.asInstanceOf[StreamTree[Double]]
            val loopBody = buildBody[Double](streamDouble, doubleBody, optimizedStream.collectionStrategy.ref)

            Block(decls, loopBody.asTerm).asExprOf[Unit]
          }

          builder.result().asInstanceOf[R]
        }

      case _ =>
        // Fallback for generic (or unhandled primitives)
        Expr.summon[scala.reflect.ClassTag[A]] match {
          case Some(ct) =>
            '{
              val builder = scala.collection.mutable.ArrayBuilder.make[A](using $ct)

              ${
                val genBody: Expr[A] => Expr[Unit] = elem => '{ builder.addOne($elem) }
                val loopBody = buildBody[A](optimizedStream.enrichedStream, genBody, optimizedStream.collectionStrategy.ref)
                Block(decls, loopBody.asTerm).asExprOf[Unit]
              }

              builder.result().asInstanceOf[R]
            }

          case None =>
            report.errorAndAbort(s"ClassTag mancante a compile-time per il tipo ${Type.show[A]}")
        }
    }
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
        // Build the loop body with all the push operations
        val push: Expr[A] => Expr[Unit] = result => {

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

  private def buildBody[A](tree: StreamTree[A], emit: Expr[A] => Expr[Unit], exitPredicates: List[Expr[Boolean]])(using Quotes): Expr[Unit] = {
    tree match {
      case source: IterableSource[A] => buildIterableSource(source, emit, exitPredicates)
      case source: ArraySource[A]    => buildArraySource(source, emit, exitPredicates)
      case filter: Filter[A]         => buildFilter(filter, emit, exitPredicates)
      case map: Map[?, ?]            => buildMap(map, emit, exitPredicates)
      case skip: EnrichedSkip[A]     => buildSkip(skip, emit, exitPredicates)
      case limit: EnrichedLimit[A]   => buildLimit(limit, emit, exitPredicates)
      case skip: Skip[A]             => ??? // TODO: if we end up here there is a optimization error
      case limit: Limit[A]           => ??? // TODO: if we end up here there is a optimization error

      case flatmap: EnrichedFlatMap[a0, b] => buildFlatMap(flatmap, emit, exitPredicates)

      case FlatMap(_, _, _, _, _, _) => ???
    }
  }
  private def buildFlatMap[A0, B](flatMap: EnrichedFlatMap[A0, B], emit: Expr[B] => Expr[Unit], exitPredicates: List[Expr[Boolean]])(using Quotes): Expr[Unit] = {

    given Type[A0] = flatMap.inType
    given Type[B] = flatMap.outType

    val flatMapEmit = (elemExpr: Expr[A0]) => {
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
    }

    // Generate the body of the upstream, emitting into the flatmap
    buildBody[A0](flatMap.upstream, flatMapEmit, exitPredicates)
  }

  private def buildLimit[A](skip: EnrichedLimit[A], emit: Expr[A] => Expr[Unit], earlyExitRef: List[Expr[Boolean]])(using Quotes): Expr[Unit] = {
    given Type[A] = skip.outType

    val counterRef: Expr[Int] = skip.counterRef
    val limit: Expr[Int] = Expr(skip.count)

    buildBody[A](
      skip.upstream,
      elem => {
        // Manually build: counter = counter + 1
        val assignTerm = Assign(counterRef.asTerm, '{ $counterRef + 1 }.asTerm)
        val incrementExpr = assignTerm.asExprOf[Unit]

        '{
          $incrementExpr
          ${ emit(elem) }
        }
      },
      earlyExitRef
    )
  }
  private def buildSkip[A](skip: EnrichedSkip[A], emit: Expr[A] => Expr[Unit], earlyExitRef: List[Expr[Boolean]])(using Quotes): Expr[Unit] = {
    given Type[A] = skip.outType

    val counterRef: Expr[Int] = skip.counterRef
    val limit: Expr[Int] = Expr(skip.count)

    buildBody[A](
      skip.upstream,
      elem => {
        // Manually build: counter = counter + 1
        val assignTerm = Assign(counterRef.asTerm, '{ $counterRef + 1 }.asTerm)
        val incrementExpr = assignTerm.asExprOf[Unit]

        '{
          if ($counterRef >= $limit) {
            ${ emit(elem) }
          } else {
            $incrementExpr
          }
        }
      },
      earlyExitRef
    )
  }

  private def buildIterableSource[A](
      source: IterableSource[A],
      emit: Expr[A] => Expr[Unit],
      earlyExitRef: List[Expr[Boolean]]
  )(using Quotes): Expr[Unit] = {
    given Type[A] = source.outType
    println("Emitting while loop over an array source")
    val exitCond = foldPredicates(earlyExitRef)
    '{
      val iterator = ${ source.term }.iterator
      // this get shifted with the match solved
      while (
        ${
          exitCond match {
            case Some(cond) => '{ iterator.hasNext && $cond }
            case None       => '{ iterator.hasNext }
          }
        }
      ) {
        val elem: A = iterator.next()
        ${ emit('elem) }
      }
    }
  }
  private def buildArraySource[A](source: ArraySource[A], emit: Expr[A] => Expr[Unit], earlyExitRef: List[Expr[Boolean]])(using Quotes): Expr[Unit] = {
    given Type[A] = source.outType
    println("Emitting while loop over an array source")

    println(s"\t ${earlyExitRef match {
        case Nil => "do not have"
        case _   => "has"
      }} early exit")

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
        val elem: A = arr(i)
        i += 1
        ${ emit('elem) }
      }
    }
  }

  private def buildFilter[A](filter: Filter[A], emit: Expr[A] => Expr[Unit], earlyExitRef: List[Expr[Boolean]])(using Quotes): Expr[Unit] = {
    given Type[A] = filter.outType

    buildBody[A](
      filter.upstream,
      elem => {
        val cond = Expr.betaReduce('{ ${ filter.predicate }($elem) })

        '{
          if ($cond) {
            ${ emit(elem) }
          }
        }
      },
      earlyExitRef
    )
  }

  private def buildMap[A, B](map: Map[A, B], emit: Expr[B] => Expr[Unit], earlyExitRef: List[Expr[Boolean]])(using Quotes): Expr[Unit] = {
    given Type[A] = map.inType
    given Type[B] = map.outType
    println(s"Map function AST: ${map.function.show}")
    buildBody[A](
      map.upstream,
      elem => {
        val mapped = Expr.betaReduce('{ ${ map.function }($elem) })
        emit(mapped)
      },
      earlyExitRef
    )
  }

}
