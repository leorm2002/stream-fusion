package fuse

import scala.annotation.compileTimeOnly
import fuse.internal.Macro
import fuse.Collector.ToArrayCollector
import fuse.Collector.SummingCollector

object FusedStream {

  /** Initialize a stream, takes as input an iterable (could be limitless) source
    */
  @compileTimeOnly("FusedStream.from can only be used in a pipeline terminated by .collect(...)")
  def from[A](source: Iterable[A]): SequentialStream[A] = compileTimeOnly

  /** Initialize a stream, takes as input an iterable (could be limitless) source
    */
  @compileTimeOnly("FusedStream.from can only be used in a pipeline terminated by .collect(...)")
  def from[A](source: java.lang.Iterable[A]): SequentialStream[A] = compileTimeOnly

  /** Creates a stream from an array, this kind of source may give better performance, especially compare to an iterable over boxed numeric types
    */
  @compileTimeOnly("FusedStream.from can only be used in a pipeline terminated by .collect(...)")
  def from[A](source: Array[A]): ParallelizableSource[A] = compileTimeOnly

  /** Creates a stream from a single element, will throw an exception if the source element is null
    * @param source
    *   the only element in the stream, must not be null
    */
  @compileTimeOnly("FusedStream.of can only be used in a pipeline terminated by .collect(...)")
  def of[A](source: A): SequentialStream[A] = compileTimeOnly

  extension [A](inline self: SequentialStream[A]) {

    /** This is the terminal operator which will trigger the collection of the elements, a custom collector may be defined by the user
      */
    inline def collect[Buf, R, S <: TerminationPolicy](inline collector: Collector[A, Buf, R, S])(using inline compileCfg: CompileConfig, runCfg: RuntimeConfig): R = ${
      Macro.collectSeq[A, Buf, R, S]('self, 'collector, 'compileCfg, 'runCfg)
    }
  }

// The parallel stream needs a special type of collector which can be
  extension [A](inline self: ParallelStream[A]) {

    /** This is the terminal operator which will trigger the collection of the elements, a custom collector may be defined by the user
      */
    inline def collect[Buf, R](inline collector: ParallelCollector[A, Buf, R])(using inline compileCfg: CompileConfig, runCfg: RuntimeConfig): R = ${
      Macro.collectPar[A, Buf, R]('self, 'collector, 'compileCfg, 'runCfg)
    }
  }

  private def compileTimeOnly: Nothing = throw new AssertionError("compile-time only")

  // Ready-to-use terminal collectors:
  // toList, toSet, findFirst, toArray, summing
  export Collector.{toList, toSet, findFirst}

  @compileTimeOnly("FusedStream.toArray can only be used as a FusedStream terminal collector")
  def toArray[T]: ToArrayCollector[T] = null.asInstanceOf[ToArrayCollector[T]]

  @compileTimeOnly("FusedStream.summing can only be used as a FusedStream terminal collector")
  def summing[T <: Summable]: SummingCollector[T] = null.asInstanceOf[SummingCollector[T]]

}
