package fuse

import scala.quoted.*
import scala.collection.mutable.ListBuffer
import scala.reflect.ClassTag
import scala.collection.mutable.ArrayBuilder
import scala.annotation.compileTimeOnly
import scala.concurrent.ExecutionContext

// Represents the possible operations on the stream
sealed trait Stream[A] {

  /** Filter the elements in the stream
    */
  def filter(pred: A => Boolean): Stream[A]

  /** Performs a mapping of the element with the given function
    */
  def map[B](f: A => B): Stream[B]

  /** The first n elements entering in this step will be discarded
    */
  def skip(n: Int): Stream[A]

  /** Will limit the number of elements exiting from this step
    */
  def limit(n: Int): Stream[A]

  /** By defining a FusedStream inside the flatMap function, you can perform a 1:N mapping (one-to-many).
    *
    * Note that the inner [[FusedStream]] must be defined inline directly within the `flatMap` body. This allows the stream transformer to fuse the inner operation into the outer
    * execution pipeline without incurring extra allocation overhead.
    *
    * ===Example===
    * {{{
    * val result = FusedStream
    *   .from(nums)
    *   .flatMap(a => FusedStream.from(InfiniteRepeater(a)).map(x => (a, x)).limit(4))
    *   .limit(20)
    *   .collect(Collector.toList)
    * }}}
    */
  def flatMap[B](f: A => Stream[B]): Stream[B]
}

object FusedStream {

  private def compileTimeOnly: Nothing = throw new AssertionError("compile-time only")

  /** Initialize a stream, takes as input an iterable (could be limitless) source
    */
  @compileTimeOnly("FusedStream.from can only be used in a pipeline terminated by .collect(...)")
  def from[A](source: Iterable[A]): Stream[A] = compileTimeOnly

  /** Initialize a stream, takes as input an iterable (could be limitless) source
    */
  @compileTimeOnly("FusedStream.from can only be used in a pipeline terminated by .collect(...)")
  def from[A](source: java.lang.Iterable[A]): Stream[A] = compileTimeOnly

  /** Creates a stream from an array, this kind of source may give better performance, especially compare to an iterable over boxed numeric types
    */
  @compileTimeOnly("FusedStream.from can only be used in a pipeline terminated by .collect(...)")
  def from[A](source: Array[A]): Stream[A] = compileTimeOnly

  /** Creates a stream from a single element, will throw an exception if the source element is null
    * @param source
    *   the only element in the stream, must not be null
    */
  @compileTimeOnly("FusedStream.of can only be used in a pipeline terminated by .collect(...)")
  def of[A](source: A): Stream[A] = compileTimeOnly

  extension [A](inline self: Stream[A]) {

    /** This is the terminal operator which will trigger the collection of the elements, a custom collector may be defined by the user
      */
    inline def collect[Buf, R](inline collector: Collector[A, Buf, R])(using inline compileCfg: CompileConfig, runCfg: RuntimeConfig): R = ${
      Macro.collectImpl[A, Buf, R]('self, 'collector, 'compileCfg, 'runCfg)
    }
  }

}

// --- Macro implementation ---
object Macro {
  def collectImpl[A: Type, Buf: Type, R: Type](stream: Expr[Stream[A]], terminal: Expr[Collector[A, Buf, R]], compileCfgExpr: Expr[CompileConfig], runCfg: Expr[RuntimeConfig])(using q: Quotes): Expr[R] = {

    val compileCfg =compileCfgExpr.valueOrAbort

    // The intermediate representation is istantiated in a class, to handle the Quotes istance being path dependand
    // the ir will passed to every step of the transpiler which will use it's quotes istnace as it's own
    val ir = StreamIr()

    // Execute the parsing of the whole expression
    val parsed = Parser(ir).parseExpression(stream, terminal)
    // logParsing(parsed)

    // Performa optimization, creates the variable and links the usage
    val optimized = Optimizer(ir).optimize(parsed)
    // logOptimized(optimized)

    // Share the operation IR between lowering and code generation, preserving the Quotes instance.
    val opIr = OPIr(ir)
    val program = OPGenerator(opIr, compileCfg).generate(optimized)
    val res = OPCodeGenerator(opIr).lower(program)
    println(s"=== FUSED STREAM GENERATED ===")
    println(res.show)
    println(s"==============================")
    res
  }

  def logOptimized[A: Type, Buf: Type, R: Type](arg0: StreamIr#AstExt[A, Buf, R])(using Quotes) = {
    val ast = exptractAstRepresentation[A, Buf, R, Phase.Enriched](arg0.enrichedStream)
    println("Optimized AST")
    println(ast)

  }

  def logParsing[A: Type, Buf: Type, R: Type](arg0: StreamIr#Ast[A, Buf, R])(using Quotes) = {
    val ast = exptractAstRepresentation[A, Buf, R, Phase.Raw](arg0.parsedStream)
    println("Parsed AST")
    println(ast)

  }

  def exptractAstRepresentation[A: Type, Buf: Type, R: Type, P <: Phase](parsedStream: StreamIr#StreamTree[P,A]): String = {

    parsedStream match {
      case w: StreamIr#WithUpstream[P,A] => s"${exptractAstRepresentation[ A, Buf, R,P](w.upstream)} \n\t-${w.getClass()}"
      case w                           => s"\t-${w.getClass()}"
    }

  }

}
