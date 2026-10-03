package fuse.internal.parallel

import fuse.internal.ir.AnyOPIR
import scala.quoted.*
import fuse.internal.OPCodeGenerator
import fuse.RuntimeConfig
import fuse.ParallelCollector

private[fuse] final class ParallelCombinerCodegen[OPIR <: AnyOPIR, G <: OPCodeGenerator[OPIR]](val conf: Expr[RuntimeConfig], val opGenerator: G) {

  private[internal] val opIr: opGenerator.opIr.type = opGenerator.opIr
  private given macroQuotes: opIr.quotes.type = opIr.quotes
  import opIr.*
  import opIr.quotes.reflect.*
  import opIr.Op.*
  import opGenerator.{lowerValue, newArray, processParallelChunks, lowerOp, lowerSupplier, lowerFinish, lowerCombine}

  private[internal] def lowerGenericCombiner[A: Type, Buf: Type, R: Type](parallel: Parallel[Buf, R], combiner: ParallelCombine.GenericCombiner[A, Buf, R]): List[Statement] = {
    val sourceSize = lowerValue(parallel.collectionSize)
    val collector = lowerValue(combiner.collector)

    val resultExpr: Expr[R] = '{
      val size = $sourceSize
      val chunks = OPCodeGenerator.computeCount(size, $conf)
      val workers = math.min($conf.workerCount, chunks)
      val partials = ${ newArray[Buf]('{ chunks }) }

      // Se no ho nessun chunk non faccio nulla
      if (chunks == 0) {
        ${ lowerFinish[R](collector, lowerSupplier[Buf](collector)) }
      } else {

        ${
          processParallelChunks('{ chunks }, '{ workers }) { chunkIdx =>
            createChunkProcessingCode[A, Buf, R](parallel, chunkIdx, '{ partials }, '{ size }, '{ chunks })
          }
        }

        var acc = partials(0)
        var i = 1
        while (i < chunks) {
          acc = ${ lowerCombine[Buf](collector, '{ acc }, '{ partials(i) }) }
          i += 1
        }
        ${ lowerFinish[R](collector, '{ acc }) }
      }
    }

    List(ValDef(parallel.returnSymbol, Some(resultExpr.asTerm.changeOwner(parallel.returnSymbol))))
  }

  private def createChunkProcessingCode[A: Type, Buf: Type, R: Type](
      parallel: Parallel[Buf, R],
      chunkIdx: Expr[Int],
      partials: Expr[Array[Buf]],
      sourceSize: Expr[Int],
      chunks: Expr[Int]
  ): Expr[Unit] = {
    val from = '{ ($chunkIdx * $sourceSize / $chunks) }
    val until = '{ ((($chunkIdx + 1) * $sourceSize) / $chunks) }
    val fromDef = ValDef(parallel.from, Some(from.asTerm))
    val untilDef = ValDef(parallel.to, Some(until.asTerm))
    val statements = parallel.statements.flatMap(lowerOp)
    val res = lowerValue(parallel.localResult)
    val resVal = '{ $partials($chunkIdx) = $res }.asTerm
    Block(fromDef :: untilDef :: statements, resVal).changeOwner(chunkIdx.asTerm.symbol.owner).asExprOf[Unit]
  }
}
