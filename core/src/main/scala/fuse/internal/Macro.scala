package fuse.internal

import scala.quoted.*
import scala.collection.mutable.ListBuffer
import scala.reflect.ClassTag
import scala.collection.mutable.ArrayBuilder
import scala.annotation.compileTimeOnly
import scala.concurrent.ExecutionContext
import fuse.Collector
import fuse.TerminationPolicy
import fuse.Exhaustive
import fuse.CompileConfig
import fuse.RuntimeConfig
import fuse.Stream
import fuse.internal.ir.{ExecutionMode, Phase, StreamIr, OPIr}
import fuse.SequentialStream

private[fuse] object Macro {

  def collectSeq[A: Type, Buf: Type, R: Type, S <: TerminationPolicy](
      stream: Expr[SequentialStream[A]],
      terminal: Expr[Collector[A, Buf, R, S]],
      compileCfgExpr: Expr[CompileConfig],
      runCfg: Expr[RuntimeConfig]
  )(using q: Quotes): Expr[R] = collectImpl(stream, terminal, compileCfgExpr, runCfg, ExecutionMode.Sequential)

  def collectPar[A: Type, Buf: Type, R: Type](
      stream: Expr[Stream[A]],
      terminal: Expr[Collector[A, Buf, R, Exhaustive]],
      compileCfgExpr: Expr[CompileConfig],
      runCfg: Expr[RuntimeConfig]
  )(using q: Quotes): Expr[R] = collectImpl(stream, terminal, compileCfgExpr, runCfg, ExecutionMode.Parallel)

  def collectImpl[A: Type, Buf: Type, R: Type, S <: TerminationPolicy](
      stream: Expr[Stream[A]],
      terminal: Expr[Collector[A, Buf, R, S]],
      compileCfgExpr: Expr[CompileConfig],
      runCfg: Expr[RuntimeConfig],
      executionMode: ExecutionMode
  )(using q: Quotes): Expr[R] = {

    val compileCfg = compileCfgExpr.valueOrAbort
    // The intermediate representation is istantiated in a class, to handle the Quotes istance being path dependand
    // the ir will passed to every step of the transpiler which will use it's quotes istnace as it's own
    val ir = StreamIr()

    val logger = new FusedLogger(compileCfg)
    // Execute the parsing of the whole expression
    val parsed = Parser(ir, logger.of("Parser")).parseExpression(stream, terminal, executionMode)

    // Performa optimization, creates the variable and links the usage
    val optimized = Optimizer(ir, logger.of("Optimizer")).optimize(parsed)

    // Share the operation IR between lowering and code generation, preserving the Quotes instance.
    val opIr = OPIr(ir)
    val program = OPGenerator(opIr, compileCfg, logger).generate(optimized)
    val res = OPCodeGenerator(opIr, runCfg, logger).lower(program)
    logger.debug(s"=== FUSED STREAM GENERATED ===")
    logger.debug(res.show)
    logger.debug(s"==============================")
    res
  }
}
