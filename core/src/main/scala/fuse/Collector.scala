package fuse
import scala.reflect.ClassTag
import scala.collection.mutable.ArrayBuilder
import scala.annotation.compileTimeOnly
import scala.collection.mutable.ListBuffer
import fuse.Collector.toList
import fuse.Collector.findFirst
import java.util.LinkedList
import scala.quoted.*
type Summable = Int | Long | Float | Double

/** We have two types of collector: the ones that accept the whole upstream and the ones that demands the stream to be stopped after some items (for example a find first)
  */
sealed trait TerminationPolicy
sealed trait Exhaustive extends TerminationPolicy
sealed trait ShortCircuiting extends TerminationPolicy

/** A collector thas is combinable has a method that permits to combine to istances of the collector, used in parallel algorithms
  */
trait Combinable[B] {
  def combine(left: B, right: B): B
}

/** Collector operations may be ordinary or inline methods. CompileConfig.strictInlining requires inline implementations at the collect call site.
  */
sealed trait CollectorBase[E, B, R] {
  def supplier(): B
  def accumulator(buf: B, E: E): Boolean
  def finisher(buf: B): R
}

trait Collector[E, B, R, S <: TerminationPolicy] extends CollectorBase[E, B, R]

/** Contract for parallel collectors:
  *   - The collector instance is shared among worker threads; it must be stateless.
  *   - `supplier` and `accumulator` are invoked concurrently across parallel chunk tasks. `supplier()` must allocate an independent, thread-confined intermediate buffer for each
  *     chunk.
  *   - Final reduction of partial chunk buffers is executed sequentially in the original chunk order.
  */
trait ParallelCollector[E, B, R] extends Collector[E, B, R, Exhaustive] with Combinable[B]

object Collector {

  // Ritornano le istanze delle classi reali
  transparent inline def toList[T]: Collector[T, ListBuffer[T], List[T], Exhaustive] = new ToListCollector[T]
  transparent inline def toSet[T]: Collector[T, scala.collection.mutable.Set[T], Set[T], Exhaustive] = new ToSetCollector[T]
  transparent inline def findFirst[T]: Collector[T, OptionBuffer[T], Option[T], ShortCircuiting] = new FindFirstCollector[T]

// The parser will recognize this and at compile time optimize into a type specialized code for primitive array speed0
  opaque type ToArrayCollector[T] <: ParallelCollector[T, ArrayBuilder[T], Array[T]] = ParallelCollector[T, ArrayBuilder[T], Array[T]]
  @compileTimeOnly("Collector.toArray can only be used as a FusedStream terminal collector")
  def toArray[T]: ToArrayCollector[T] = null.asInstanceOf[ToArrayCollector[T]]

  /** A specialized parallel collector for numeric sums (Int, Long, Float, Double).
    *
    * Note on Float and Double sums in parallel streams: Floating-point addition is non-associative due to rounding error. Consequently, parallel summation of Float or Double
    * streams may yield results that vary slightly depending on the degree of chunking and concurrency
    */
  opaque type SummingCollector[T <: Summable] <: ParallelCollector[T, Any, T] = ParallelCollector[T, Any, T]
  @compileTimeOnly("Collector.summing can only be used as a FusedStream terminal collector")
  def summing[T <: Summable]: SummingCollector[T] = null.asInstanceOf[SummingCollector[T]]

// Compiler specialized Bfer
  final class OptionBuffer[@specialized(Int, Long, Double) T] {
    private var value: T = scala.compiletime.uninitialized
    var isDefined: Boolean = false

    def set(v: T): Unit = {
      value = v
      isDefined = true
    }

    def toOption: Option[T] = if (isDefined) Some(value) else None
  }

  final class ToListCollector[T] extends Collector[T, ListBuffer[T], List[T], Exhaustive] {
    inline def supplier(): ListBuffer[T] = ListBuffer.empty[T]
    inline def accumulator(buf: ListBuffer[T], E: T): Boolean = { buf.addOne(E); false }
    inline def finisher(buf: ListBuffer[T]): List[T] = buf.toList
  }

  final class ToSetCollector[T] extends Collector[T, scala.collection.mutable.Set[T], Set[T], Exhaustive] {
    inline def supplier(): scala.collection.mutable.Set[T] = scala.collection.mutable.Set.empty[T]
    inline def accumulator(buf: scala.collection.mutable.Set[T], E: T): Boolean = { buf.addOne(E); false }
    inline def finisher(buf: scala.collection.mutable.Set[T]): Set[T] = buf.toSet
  }

  final class FindFirstCollector[T] extends Collector[T, Collector.OptionBuffer[T], Option[T], ShortCircuiting] {
    inline def supplier(): Collector.OptionBuffer[T] = new Collector.OptionBuffer[T]
    inline def accumulator(buf: Collector.OptionBuffer[T], E: T): Boolean = {
      buf.set(E)
      true // Trovato il primo Eento: segnala all'engine di interrompere il ciclo
    }
    inline def finisher(buf: Collector.OptionBuffer[T]): Option[T] = buf.toOption
  }
}
