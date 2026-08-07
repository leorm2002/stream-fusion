package fuse

import scala.quoted.*
import scala.collection.mutable.ListBuffer
import scala.reflect.ClassTag
import scala.collection.mutable.ArrayBuilder

object streamInternal {
  opaque type Stream[A] = Any
}

import streamInternal.Stream

object FusedStream {

  /** Initialize a stream, takes as input an iterable (could be limitless) source
    */
  def from[A](source: Iterable[A]): Stream[A] = throw new Error("`from` should never be called at runtime!")

  /** Creates a stream from an array, this kind of source may give better performance, especially compare to an iterable over boxed numeric types
    */
  def from[A](source: Array[A]): Stream[A] = throw new Error("`from` should never be called at runtime!")

  /** Creates a stream from a single element, will throw an exception if the source element is null
    * @param source
    *   the only element in the stream, must not be null
    */
  def of[A](source: A): Stream[A] = throw new Error("`from` should never be called at runtime!")

  // Chain methods
  extension [A](self: Stream[A]) {

    /** Filter the elements in the stream
      */
    def filter(pred: A => Boolean): Stream[A] = throw new Error("`filter` should never be called at runtime!")

    /** Performs a mapping of the element with the given function
      */
    def map[B](f: A => B): Stream[B] = throw new Error("`map` should never be called at runtime!")

    /** The first n elements entering in this step will be discarded
      */
    def skip(skip: Int): Stream[A] = throw new Error("`skip` should never be called at runtime!")

    /** Will limit the number of elements exiting from this step
      */
    def limit(skip: Int): Stream[A] = throw new Error("`limit` should never be called at runtime!")

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
    def flatMap[B](f: A => Stream[B]): Stream[B] = throw new Error("`flatMap` should never be called at runtime!")
  }

  extension [A](inline self: Stream[A]) {

    /** This is the terminal operator which will trigger the collection of the elements, a custom collector may be defined by the user
      */
    inline def collect[Buf, R](inline collector: Collector[A, Buf, R]): R = ${
      collectImpl[A, Buf, R]('self, 'collector)
    }
  }

  // --- Macro implementation ---
  def collectImpl[A: Type, Buf: Type, R: Type](stream: Expr[Stream[A]], terminal: Expr[Collector[A, Buf, R]])(using q: Quotes): Expr[R] = {

    // The intermediate representation is istantiated in a class, to handle the Quotes istance being path dependand
    // the ir will passed to every step of the transpiler which will use it's quotes istnace as it's own
    val ir = StreamIr()

    // Execute the parsing of the whole expression
    val parsed = Parser(ir).parseExpression(stream, terminal)
    // logParsing(parsed)

    // Performa optimization, creates the variable and links the usage
    val optimized = Optimizer(ir).optimize(parsed)
    // logOptimized(optimized)

    // Given the sequence of operation generate the code
    val res = CodeGenerator(ir).generateCode(optimized)
    println(s"=== FUSED STREAM GENERATED ===")
    println(res.show)
    println(s"==============================")
    res
  }
  def logOptimized[A: Type, Buf: Type, R: Type](arg0: StreamIr#AstExt[A, Buf, R])(using Quotes) = {
    val ast = exptractAstRepresentation[A, Buf, R](arg0.enrichedStream)
    println("Optimized AST")
    println(ast)

  }

  def logParsing[A: Type, Buf: Type, R: Type](arg0: StreamIr#Ast[A, Buf, R])(using Quotes) = {
    val ast = exptractAstRepresentation[A, Buf, R](arg0.parsedStream)
    println("Parsed AST")
    println(ast)

  }

  def exptractAstRepresentation[A: Type, Buf: Type, R: Type](parsedStream: StreamIr#StreamTree[A]): String = {

    parsedStream match {
      case w: StreamIr#WithUpstream[A] => s"${exptractAstRepresentation[A, Buf, R](w.upstream)} \n\t-${w.getClass()}"
      case w                           => s"\t-${w.getClass()}"
    }

  }
}
