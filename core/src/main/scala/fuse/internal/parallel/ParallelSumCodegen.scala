package fuse.internal.parallel

import fuse.internal.ir.AnyOPIR
import fuse.Summable
import scala.quoted.*
import fuse.internal.OPCodeGenerator
import fuse.RuntimeConfig

private[fuse] final class ParallelSumCodegen[OPIR <: AnyOPIR, G <: OPCodeGenerator[OPIR]](val conf: Expr[RuntimeConfig], val opGenerator: G) {

  private[internal] val opIr: opGenerator.opIr.type = opGenerator.opIr
  private given macroQuotes: opIr.quotes.type = opIr.quotes
  import opIr.*
  import opIr.quotes.reflect.*
  import opIr.Op.*
  import opGenerator.{lowerValue, newArray, processParallelChunks, lowerOp}

  private[internal] def lowerParallelSum[T <: Summable: Type](parallel: Parallel[T]): List[Statement] = {
    val sourceSize = lowerValue(parallel.collectionSize)
    val resultExpr: Expr[T] = '{
      val size = $sourceSize
      val chunks = OPCodeGenerator.computeCount(size, $conf)
      val workers = math.min($conf.workerCount, chunks)
      val partials = ${ newArray[T]('{ chunks }) }
      ${
        processParallelChunks('{ chunks }, '{ workers }) { chunkIdx =>
          createChunkProcessingCode(parallel, chunkIdx, '{ partials }, '{ size }, '{ chunks })
        }
      }
      ${ reduceParallelSum[T]('{ partials }, '{ chunks }) }
    }
    List(ValDef(parallel.returnSymbol, Some(resultExpr.asTerm.changeOwner(parallel.returnSymbol))))
  }

  private def createChunkProcessingCode[T: Type](par: Parallel[T], idx: Expr[Int], partials: Expr[Array[T]], sourceSize: Expr[Int], chunks: Expr[Int]): Expr[Unit] = {
    val from = '{ ($idx.toLong * $sourceSize / $chunks).toInt }
    val until = '{ (($idx.toLong + 1L) * $sourceSize / $chunks).toInt }
    val fromDef = ValDef(par.from, Some(from.asTerm))
    val untilDef = ValDef(par.to, Some(until.asTerm))
    val statements = par.statements.flatMap(lowerOp)
    val res = lowerValue(par.localResult)
    val resVal = '{ $partials($idx) = $res }.asTerm
    Block(fromDef :: untilDef :: statements, resVal).changeOwner(idx.asTerm.symbol.owner).asExprOf[Unit]
  }

  // Specialization for the specialized sum operator
  private def reduceParallelSum[T <: Summable: Type](partials: Expr[Array[T]], chunks: Expr[Int]): Expr[T] = {
    Type.of[T] match {
      case '[Int] =>
        val arr = partials.asInstanceOf[Expr[Array[Int]]]
        '{
          var result = 0
          var i = 0
          while (i < $chunks) {
            result += $arr(i)
            i += 1
          }
          result
        }.asExprOf[T]

      case '[Long] =>
        val arr = partials.asInstanceOf[Expr[Array[Long]]]
        '{
          var result = 0L
          var i = 0

          while (i < $chunks) {
            result += $arr(i)
            i += 1
          }

          result
        }.asExprOf[T]

      case '[Float] =>
        val arr = partials.asInstanceOf[Expr[Array[Float]]]

        '{
          var result = 0.0f
          var i = 0

          while (i < $chunks) {
            result += $arr(i)
            i += 1
          }

          result
        }.asExprOf[T]

      case '[Double] =>
        val arr = partials.asInstanceOf[Expr[Array[Double]]]

        '{
          var result = 0.0d
          var i = 0

          while (i < $chunks) {
            result += $arr(i)
            i += 1
          }

          result
        }.asExprOf[T]
    }
  }

}
