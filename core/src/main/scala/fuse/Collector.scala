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

/**
  * We have two types of collector: the ones that accept the whole upstream and the ones that demands the stream to be stopped after some items (for example a find first)
  */
sealed trait StopPolicy
sealed trait NoEarlyStopping extends StopPolicy
sealed trait HasEarlyStopping extends StopPolicy

/**
  * A collector thas is combinable has a method that permits to combine to istances of the collector, used in parallel algorithms
  */
trait Combinable[Buf] {
  def combine(left: Buf, right: Buf): Buf
}

trait CollectorBase[ELEM, Buf, RET] {
  def supplier(count: Int): Buf = supplier()
  def supplier(): Buf
  def accumulator(buf: Buf, elem: ELEM): Boolean
  def finisher(buf: Buf): RET
}

trait Collector[ELEM, Buf, RET, S <: StopPolicy] extends CollectorBase[ELEM, Buf, RET]

/**
  * A parallel collector: it have alle the attributes to be used in a parallel algorithm: it' combinable and have no early exit
  */
trait ParallelCollector[ELEM, Buf, RET] extends Collector[ELEM, Buf, RET, NoEarlyStopping] with Combinable[Buf]

final class ToListCollector[T] extends Collector[T, ListBuffer[T], List[T], NoEarlyStopping] {
  def supplier(): ListBuffer[T] = ListBuffer.empty[T]
  def accumulator(buf: ListBuffer[T], elem: T): Boolean = { buf.addOne(elem); false }
  def finisher(buf: ListBuffer[T]): List[T] = buf.toList
}

final class ToSetCollector[T] extends Collector[T, scala.collection.mutable.Set[T], Set[T], NoEarlyStopping] {
  def supplier(): scala.collection.mutable.Set[T] = scala.collection.mutable.Set.empty[T]
  def accumulator(buf: scala.collection.mutable.Set[T], elem: T): Boolean = { buf.addOne(elem); false }
  def finisher(buf: scala.collection.mutable.Set[T]): Set[T] = buf.toSet
}

final class FindFirstCollector[T] extends Collector[T, Collector.OptionBuffer[T], Option[T], HasEarlyStopping] {
  def supplier(): Collector.OptionBuffer[T] = new Collector.OptionBuffer[T]
  def accumulator(buf: Collector.OptionBuffer[T], elem: T): Boolean = {
    buf.set(elem)
    true // Trovato il primo elemento: segnala all'engine di interrompere il ciclo
  }
  def finisher(buf: Collector.OptionBuffer[T]): Option[T] = buf.toOption
}

object Collector {

  // Ritornano le istanze delle classi reali
  def toList[T]: Collector[T, ListBuffer[T], List[T], NoEarlyStopping] = new ToListCollector[T]
  def toSet[T]: Collector[T, scala.collection.mutable.Set[T], Set[T], NoEarlyStopping] = new ToSetCollector[T]
  def findFirst[T]: Collector[T, OptionBuffer[T], Option[T], HasEarlyStopping] = new FindFirstCollector[T]

// The parser will recognize this and at compile time optimize into a type specialized code for primitive array speed0
  opaque type ToArrayCollector[T] <: ParallelCollector[T, ArrayBuilder[T], Array[T]] = ParallelCollector[T, ArrayBuilder[T], Array[T]]
  @compileTimeOnly("Collector.toArray can only be used as a FusedStream terminal collector")
  def toArray[T]: ToArrayCollector[T] = null.asInstanceOf[ToArrayCollector[T]]

  opaque type SummingCollector[T <: Summable] <: ParallelCollector[T, Any, T] = ParallelCollector[T, Any, T]
  @compileTimeOnly("Collector.summing can only be used as a FusedStream terminal collector")
  def summing[T <: Summable]: SummingCollector[T] = null.asInstanceOf[SummingCollector[T]]

// Compiler specialized buffer
  final class OptionBuffer[@specialized(Int, Long, Double) T] {
    private var value: T = scala.compiletime.uninitialized
    var isDefined: Boolean = false

    def set(v: T): Unit = {
      value = v
      isDefined = true
    }

    def toOption: Option[T] = if (isDefined) Some(value) else None
  }
}
