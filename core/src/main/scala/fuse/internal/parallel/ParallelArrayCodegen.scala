package fuse.internal.parallel

import fuse.internal.ir.AnyOPIR
import scala.quoted.*
import fuse.internal.OPCodeGenerator
import fuse.RuntimeConfig

private[fuse] final class ParallelArrayCodegen[OPIR <: AnyOPIR, G <: OPCodeGenerator[OPIR]](val conf: Expr[RuntimeConfig], val opGenerator: G) {

  private[internal] val opIr: opGenerator.opIr.type = opGenerator.opIr
  private given macroQuotes: opIr.quotes.type = opIr.quotes
  import opIr.*
  import opIr.quotes.reflect.*
  import opIr.Op.*
  import opGenerator.{lowerValue, newArray, processParallelChunks, lowerOp}

  private[internal] def lowerArrayConcat[E: Type](parallel: Parallel[Array[E]], concat: ParallelCombine.ArrayConcat[E]): List[Statement] = {
    val sourceSize = lowerValue(parallel.collectionSize)

    val resultExpr: Expr[Array[E]] = '{
      val size = $sourceSize
      val chunks = OPCodeGenerator.computeCount(size, $conf)
      val workers = math.min($conf.workerCount, chunks)
      val partials = ${ newArray[Array[E]]('{ chunks }) }
      val sizes: Array[Int] = ${ concat.validSize.fold('{ null.asInstanceOf[Array[Int]] })(_ => newArray[Int]('{ chunks })) }

      ${

        processParallelChunks('{ chunks }, '{ workers }) { chunkIdx =>
          createArrayChunkProcessingCode(parallel, concat, chunkIdx, '{ partials }, '{ sizes }, '{ size }, '{ chunks })
        }
      }
      ${
        concat.validSize match {
          case Some(_) => reducePartialArrays[E]('{ partials }, '{ sizes }, '{ chunks })
          case _       => reduceCompleteArrays[E]('{ partials }, '{ chunks })
        }
      }
    }
    List(ValDef(parallel.returnSymbol, Some(resultExpr.asTerm.changeOwner(parallel.returnSymbol))))
  }

  private def createArrayChunkProcessingCode[E: Type](
      parallel: Parallel[Array[E]],
      concat: ParallelCombine.ArrayConcat[E],
      chunkIdx: Expr[Int],
      partials: Expr[Array[Array[E]]],
      sizes: Expr[Array[Int]],
      sourceSize: Expr[Int],
      chunks: Expr[Int]
  ): Expr[Unit] = {

    val from = '{ ($chunkIdx.toLong * $sourceSize / $chunks).toInt }
    val until = '{ ((($chunkIdx + 1).toLong * $sourceSize) / $chunks).toInt }
    val fromDef = ValDef(parallel.from, Some(from.asTerm))
    val untilDef = ValDef(parallel.to, Some(until.asTerm))
    val statements = parallel.statements.flatMap(lowerOp)
    val localArray = lowerValue(parallel.localResult)

    val emit =
      concat.validSize match {
        case Some(x) =>
          val validSize = lowerValue(x)
          '{
            $partials($chunkIdx) = $localArray
            $sizes($chunkIdx) = $validSize
          }

        case None => '{ $partials($chunkIdx) = $localArray }
      }

    Block(fromDef :: untilDef :: statements, emit.asTerm).changeOwner(chunkIdx.asTerm.symbol.owner).asExprOf[Unit]
  }

  private def reducePartialArrays[E: Type](partials: Expr[Array[Array[E]]], sizes: Expr[Array[Int]], chunks: Expr[Int]): Expr[Array[E]] = {
    '{
      var totalSize = 0
      var i = 0
      while (i < $chunks) {
        totalSize += $sizes(i)
        i += 1
      }
      val result = ${ newArray[E]('{ totalSize }) }
      var offset = 0
      i = 0
      while (i < $chunks) {
        val count = $sizes(i)
        if (count > 0) {
          System.arraycopy($partials(i), 0, result, offset, count)
          offset += count
        }
        i += 1
      }
      result
    }
  }

  private def reduceCompleteArrays[E: Type](partials: Expr[Array[Array[E]]], chunks: Expr[Int]): Expr[Array[E]] = {
    '{
      var totalSize = 0
      var i = 0
      while (i < $chunks) {
        totalSize += $partials(i).length
        i += 1
      }
      val result = ${ newArray[E]('{ totalSize }) }
      var offset = 0
      i = 0
      while (i < $chunks) {
        val count = $partials(i).length
        if (count > 0) {
          System.arraycopy($partials(i), 0, result, offset, count)
          offset += count
        }
        i += 1
      }
      result
    }
  }

  private[internal] def lowerArrayDirect[E: Type](parallel: Parallel[Array[E]]): List[Statement] = {
    val sourceSize = lowerValue(parallel.collectionSize)

    val processExpr: Expr[Unit] = '{
      val size = $sourceSize
      val chunks = OPCodeGenerator.computeCount(size, $conf)
      val workers = math.min($conf.workerCount, chunks)
      ${
        processParallelChunks('{ chunks }, '{ workers }) { chunkIdx =>
          createDirectChunkProcessingCode(parallel, chunkIdx, '{ size }, '{ chunks })
        }
      }
    }

    List(processExpr.asTerm)
  }

  private def createDirectChunkProcessingCode[E: Type](parallel: Parallel[Array[E]], chunkIdx: Expr[Int], sourceSize: Expr[Int], chunks: Expr[Int]): Expr[Unit] = {
    val from = '{ ($chunkIdx.toLong * $sourceSize / $chunks).toInt }
    val until = '{ ((($chunkIdx + 1).toLong * $sourceSize) / $chunks).toInt }
    val fromDef = ValDef(parallel.from, Some(from.asTerm))
    val untilDef = ValDef(parallel.to, Some(until.asTerm))
    val statements = parallel.statements.flatMap(lowerOp)
    Block(fromDef :: untilDef :: statements, Literal(UnitConstant())).changeOwner(chunkIdx.asTerm.symbol.owner).asExprOf[Unit]
  }
}
