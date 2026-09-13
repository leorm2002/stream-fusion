package fuse.internal

import scala.quoted.*
import fuse.internal.ir.AnyOPIR
import fuse.internal.ArrayListAccessor
import fuse.Summable
import fuse.RuntimeConfig
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CountDownLatch
import fuse.internal.parallel.ParallelSumCodegen
import fuse.internal.parallel.ParallelArrayCodegen
import scala.annotation.static
import java.util.concurrent.atomic.AtomicReference

/** Emits Scala code from an operation program, independently of stream lowering. */
private[internal] final class OPCodeGenerator[OPIR <: AnyOPIR](val opIr: OPIR, runCfg: Expr[RuntimeConfig]) {
  private given macroQuotes: opIr.quotes.type = opIr.quotes

  import opIr.*
  import opIr.quotes.reflect.*
  import opIr.Op.*

  private var conf: Expr[RuntimeConfig] = scala.compiletime.uninitialized

  def lower[OUT](program: Program[OUT])(using Type[OUT]): Expr[OUT] = {
    '{
      val conff = $runCfg
      ${
        conf = '{ conff }
        lowerProgram(program)
      }
    }

  }
  def lowerProgram[OUT](program: Program[OUT])(using Type[OUT]): Expr[OUT] = {
    val statements = program.statements.flatMap(lowerOp)
    val result = lowerValue(program.result)
    Block(statements, result.asTerm).asExprOf[OUT]
  }

  def lowerOp(op: Op): List[Statement] = {
    op match {
      case parallel: Parallel[t] => {
        given Type[t] = parallel.localResult.valueType
        parallel.combiner match {
          case sum: ParallelCombine.Sum[t]            => new ParallelSumCodegen[OPIR, this.type](conf, this).lowerParallelSum(parallel)
          case concat: ParallelCombine.ArrayConcat[e] => {
            given Type[e] = concat.elemType
            new ParallelArrayCodegen[OPIR, this.type](conf, this).lowerArrayConcat[e](parallel.asInstanceOf[Parallel[Array[e]]], concat)
          }
          case arrayDirect: ParallelCombine.ArrayDirect[e] => {
            given Type[e] = arrayDirect.elemType
            new ParallelArrayCodegen[OPIR, this.type](conf, this).lowerArrayDirect[e](parallel.asInstanceOf[Parallel[Array[e]]])
          }
        }
      }
      case ExternalStatement(statement) => List(statement)
      case CodeBlock(ops)               => List(Block(ops.flatMap(lowerOp), Literal(UnitConstant())))
      case declare: Declare[t]          => {
        given Type[t] = declare.initialValue.valueType
        // Initializers can contain lambdas or local classes whose definitions now belong to this val.
        val initializer = lowerValue[t](declare.initialValue).asTerm.changeOwner(declare.symbol)
        List(ValDef(declare.symbol, Some(initializer)))
      }
      case assign: AssignVal[t] => {
        given Type[t] = assign.value.valueType
        val valueExpr = lowerValue[t](assign.value)
        List(Assign(Ref(assign.symbol), valueExpr.asTerm))
      }
      case Inc(symbol) => {
        val counterRef = Ref(symbol)
        val incrementTerm = Assign(counterRef, Select.overloaded(counterRef, "+", Nil, List(Literal(IntConstant(1)))))
        List(incrementTerm)
      }
      case Op.If(condition, thenBody, elseBody) => {
        val cond = lowerValue(condition)
        val thenBodyStatements = generateBlock(thenBody)
        val ifExpr = elseBody match {
          case Some(value) => {
            val elseBodyStatements = generateBlock(value)
            '{
              if ($cond) { $thenBodyStatements }
              else { $elseBodyStatements }
            }
          }
          case None => { '{ if ($cond) { $thenBodyStatements } } }
        }

        List(ifExpr.asTerm)
      }
      case Op.While(condition, body) => {
        val expr = lowerValue(condition);
        val bodyExpr: Expr[Unit] = generateBlock(body)
        val whileExpr = '{ while ($expr) { $bodyExpr } }
        List(whileExpr.asTerm)
      }
      case Compute(value) => List(generateAnyValue(value).asTerm)

      case add: DynamicArrayBuilderAdd[t] => {
        given Type[t] = add.elemType
        val builder = lowerValue(add.builder)
        val elem = lowerValue(add.elem)
        List('{ $builder.addOne($elem); () }.asTerm)
      }
      case write: ArrayWrite[t] => {
        given Type[t] = write.valueType
        val array = lowerValue[Array[t]](write.array)
        val index = lowerValue[Int](write.index)
        val value = lowerValue[t](write.value)
        List('{ $array($index) = $value }.asTerm)
      }
    }
  }

  def lowerValue[T: Type](value: Value[T]): Expr[T] = {
    value match {
      // Crea un Term da un quotes.reflect.Symbol e lo converte in Expr[T]
      case SymbolRef(symbol) => Ref(symbol).asExprOf[T]
      case ScalaExpr(expr)   => expr
      case ConstantVal(expr) => expr

      case LessThan(left, right) => {
        val l = lowerValue(left)
        val r = lowerValue(right)
        '{ $l < $r }.asExprOf[T]
      }

      case GreaterThanOrEqual(left, right) => {
        val l = lowerValue(left)
        val r = lowerValue(right)
        '{ $l >= $r }.asExprOf[T]
      }

      case equal: Equal[t] => {
        given Type[t] = equal.left.valueType
        val l = lowerValue(equal.left)
        val r = lowerValue(equal.right)
        '{ $l == $r }.asExprOf[T]
      }

      case And(left, right) => {
        val l = lowerValue(left)
        val r = lowerValue(right)
        '{ $l && $r }.asExprOf[T]
      }
      case IfValue(condition, thenValue, elseValue) => {
        val cond = lowerValue(condition)
        val thenExpr = lowerValue(thenValue)
        val elseExpr = lowerValue(elseValue)
        '{ if ($cond) $thenExpr else $elseExpr }
      }

      case test: IsInstanceOf[t] =>
        given Type[t] = test.testedType
        val value = generateAnyValue(test.value)
        '{ $value.isInstanceOf[t] }.asExprOf[T]

      case cast: AsInstanceOf[t] =>
        given Type[t] = cast.valueType
        val value = generateAnyValue(cast.value)
        '{ $value.asInstanceOf[t] }.asExprOf[T]

      case ArrayRead(array, index) =>
        val arr = lowerValue(array)
        val idx = lowerValue(index)
        '{ $arr($idx) }.asExprOf[T]

      case length: ArrayLength[t] =>
        given Type[t] = length.elemType
        val array = lowerValue(length.array)
        '{ $array.length }.asExprOf[T]

      case take: ArrayTake[t] =>
        given Type[t] = take.elemType
        val array = lowerValue(take.array)
        val count = lowerValue(take.count)
        '{ $array.take($count) }.asExprOf[T]

      case read: JListRead[t] =>
        given Type[t] = read.valueType
        val list = lowerValue(read.list)
        val index = lowerValue(read.index)
        '{ $list.get($index) }.asExprOf[T]

      case raw: ArrayListRawArray[t] =>
        given Type[t] = raw.elemType
        val list = lowerValue(raw.list)
        '{ ArrayListAccessor.getRawArray($list.asInstanceOf[java.util.ArrayList[t]]) }.asExprOf[T]

      case iterator: IteratorOf[t] =>
        given Type[t] = iterator.elemType
        val source = lowerValue(iterator.source)
        '{ $source.iterator }.asExprOf[T]

      case hasNext: HasNext[t] =>
        given Type[t] = hasNext.elemType
        val iterator = lowerValue(hasNext.iterator)
        '{ $iterator.hasNext }.asExprOf[T]

      case next: Next[t] =>
        given Type[t] = next.valueType
        val iterator = lowerValue(next.iterator)
        '{ $iterator.next() }.asExprOf[T]

      case iterator: JIteratorOf[t] =>
        given Type[t] = iterator.elemType
        val source = lowerValue(iterator.source)
        '{ $source.iterator() }.asExprOf[T]

      case hasNext: JHasNext[t] =>
        given Type[t] = hasNext.elemType
        val iterator = lowerValue(hasNext.iterator)
        '{ $iterator.hasNext() }.asExprOf[T]

      case next: JNext[t] =>
        given Type[t] = next.valueType
        val iterator = lowerValue(next.iterator)
        '{ $iterator.next() }.asExprOf[T]

      case builder: DynamicArrayBuilderNew[t] =>
        given Type[t] = builder.elemType
        '{ new scala.collection.mutable.ArrayBuffer[t]() }.asExprOf[T]

      case result: DynamicArrayBuilderResult[t] =>
        given Type[t] = result.elemType
        generateBuilderResult(result.builder).asExprOf[T]

      case supplier: CollectorSupplier[a, buf, r] =>
        given Type[a] = supplier.elemType
        given Type[buf] = supplier.valueType
        given Type[r] = supplier.resultType
        val collector = lowerValue(supplier.collector)
        '{ $collector.supplier() }.asExprOf[T]

      case accumulate: CollectorAccumulate[a, buf, r] =>
        given Type[a] = accumulate.elemType
        given Type[buf] = accumulate.bufferType
        given Type[r] = accumulate.resultType
        val collector = lowerValue(accumulate.collector)
        val buffer = lowerValue(accumulate.buffer)
        val elem = lowerValue(accumulate.elem)
        '{ $collector.accumulator($buffer, $elem) }.asExprOf[T]

      case finish: CollectorFinish[a, buf, r] =>
        given Type[a] = finish.elemType
        given Type[buf] = finish.bufferType
        given Type[r] = finish.valueType
        val collector = lowerValue(finish.collector)
        val buffer = lowerValue(finish.buffer)
        '{ $collector.finisher($buffer) }.asExprOf[T]

      case app: ApplyFun[in, out] =>
        given Type[in] = app.inType
        given Type[out] = app.valueType
        val arg: Expr[in] = lowerValue[in](app.argument)
        Expr.betaReduce('{ ${ app.function }($arg) }).asExprOf[T]

      case add: Add[t] =>
        given Type[t] = add.valueType
        val l = lowerValue[t](add.left).asTerm
        val r = lowerValue[t](add.right).asTerm
        Select.overloaded(l, "+", Nil, List(r)).asExprOf[T]
      case arr: ArrayDefine[t] =>
        given Type[t] = arr.elemType
        newArray[t](lowerValue(arr.size)).asExprOf[T]
      case Subtract(left, right) =>
        val l = lowerValue(left)
        val r = lowerValue(right)
        '{ $l - $r }.asExprOf[T]
    }
  }

  private def generateBlock(op: Op): Expr[Unit] =
    op match {
      case CodeBlock(ops) => Block(ops.flatMap(lowerOp), Literal(UnitConstant())).asExprOf[Unit]
      case other          => Block(lowerOp(other), Literal(UnitConstant())).asExprOf[Unit]
    }

  private def generateAnyValue(value: Value[?]): Expr[Any] = value match {
    case value: Value[t] =>
      given Type[t] = value.valueType
      lowerValue(value)
  }

  private def generateBuilderResult[A: Type](builder: Value[DynamicArrayBuilder[A]]): Expr[Array[A]] = {
    val builderExpr = lowerValue(builder)
    // Materialize the builder once and allocate the concrete array type without requiring a runtime ClassTag.
    '{
      val buffer = $builderExpr
      val result = ${ newArray[A]('{ buffer.size }) }
      buffer.copyToArray(result)
      result
    }
  }

  def newArray[A: Type](size: Expr[Int]): Expr[Array[A]] = {
    val ctor = Select(New(TypeIdent(defn.ArrayClass)), defn.ArrayClass.primaryConstructor)
    val typedCtor = TypeApply(ctor, List(Inferred(TypeRepr.of[A])))
    Apply(typedCtor, List(size.asTerm)).asExprOf[Array[A]]
  }

  // Specialization for the specialized sum operator

  def processParallelChunks(chunks: Expr[Int], workers: Expr[Int])(processChunk: Expr[Int] => Expr[Unit]): Expr[Unit] = {
    '{
      val failed = new AtomicReference[Throwable](null)
      val nextChunk = new AtomicInteger($workers)
      val latch = new CountDownLatch($workers)
      var workerIdx = 0

      while (workerIdx < $workers) {
        val firstChunk = workerIdx
        try {

          $conf.ec.execute(
            new Runnable {
              override def run(): Unit = {
                var chunkIdx = firstChunk
                try {
                  while (chunkIdx < $chunks) {
                    ${ processChunk('{ chunkIdx }) }
                    chunkIdx = nextChunk.getAndIncrement()
                  }
                } catch {
                  case fail: Throwable => failed.compareAndSet(null, fail)
                } finally {
                  latch.countDown()
                }
              }
            }
          )
        } catch {
          case fail: Throwable => {
            failed.compareAndSet(null, fail)
            latch.countDown()
          }
        }
        workerIdx += 1
      }
      scala.concurrent.blocking { latch.await() }

      val error = failed.get()
      if (error != null) {
        throw error
      }
    }
  }
}

object OPCodeGenerator {
  inline def computeCount(size: Int, config: RuntimeConfig): Int = {
    if (size == 0) 0
    else if (config.workerCount == 1) 1
    else math.min(size.toLong, config.workerCount.toLong * config.chunksPerWorker).toInt
  }

}
