package consumer

import munit.FunSuite
import scala.compiletime.testing.typeCheckErrors
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext
import fuse.*
import fuse.FusedStream.*

class CollectorAnonymousClassNotAcceptedTest extends FunSuite {
  final class NotInlineListCollector[A] extends ParallelCollector[A, ListBuffer[A], List[A]] {
    def supplier(): ListBuffer[A] = ListBuffer.empty[A]
    def accumulator(buf: ListBuffer[A], elem: A): Boolean = { buf.addOne(elem); false }
    def finisher(buf: ListBuffer[A]): List[A] = buf.toList
    def combine(left: ListBuffer[A], right: ListBuffer[A]): ListBuffer[A] = { left.addAll(right); left }
  }

  final class InlineListCollector[A] extends ParallelCollector[A, ListBuffer[A], List[A]] {
    inline def supplier(): ListBuffer[A] = ListBuffer.empty[A]
    inline def accumulator(buf: ListBuffer[A], elem: A): Boolean = { buf.addOne(elem); false }
    inline def finisher(buf: ListBuffer[A]): List[A] = buf.toList
    inline def combine(left: ListBuffer[A], right: ListBuffer[A]): ListBuffer[A] = { left.addAll(right); left }
  }

  private val expected = "collect requires a named concrete collector class when strictInlining is enabled"

  test("concrete inline collectors are rejected by the default configuration") {
    val errors = typeCheckErrors("""
      import scala.collection.mutable.ListBuffer
      import fuse.*
      import fuse.FusedStream.*
      FusedStream.from(Array(1, 2)).collect(new NotInlineListCollector())
    """)
    val exp = "StreamFusion: collector method 'supplier' must have a concrete inline implementation when strictInlining is enabled"
    assert(errors.exists(_.message.contains(exp)), errors.map(_.message).mkString("\n"))
  }

  test("direct anonymous inline collectors are rejected by the default configuration") {
    val errors = typeCheckErrors("""
      import scala.collection.mutable.ListBuffer
      import fuse.*
      import fuse.FusedStream.*
      FusedStream.from(Array(1, 2)).collect(new Collector[Int, ListBuffer[Int], List[Int], Exhaustive] {
        inline def supplier(): ListBuffer[Int] = ListBuffer.empty[Int]
        inline def accumulator(buf: ListBuffer[Int], E: Int): Boolean = { buf.addOne(E); false }
        inline def finisher(buf: ListBuffer[Int]): List[Int] = buf.toList
      })
    """)

    assert(errors.exists(_.message.contains(expected)), errors.map(_.message).mkString("\n"))
  }

  test("anonymous collectors work with strictInlining explicitly disabled") {
    inline given CompileConfig = CompileConfig(false, false, strictInlining = false)
    val result = FusedStream
      .from(Array(1, 2, 3))
      .collect(new Collector[Int, ListBuffer[Int], List[Int], Exhaustive] {
        def supplier(): ListBuffer[Int] = ListBuffer.empty[Int]
        def accumulator(buf: ListBuffer[Int], elem: Int): Boolean = { buf.addOne(elem); false }
        def finisher(buf: ListBuffer[Int]): List[Int] = buf.toList
      })
    assertEquals(result, List(1, 2, 3))
  }

  test("named and intrinsic collectors work with the default strict configuration") {
    given RuntimeConfig = RuntimeConfig(ExecutionContext.parasitic, workerCount = 2, chunksPerWorker = 2)
    val values = Array(1, 2, 3)
    assertEquals(FusedStream.from(values).collect(new InlineListCollector[Int]), values.toList)
    assertEquals(FusedStream.from(values).parallel().collect(new InlineListCollector[Int]), values.toList)
    assertEquals(FusedStream.from(values).collect(toList), values.toList)
    assertEquals(FusedStream.from(values).collect(toArray).toList, values.toList)
    assertEquals(FusedStream.from(values).collect(summing), values.sum)
    assertEquals(FusedStream.from(values).parallel().collect(toArray).toList, values.toList)
    assertEquals(FusedStream.from(values).parallel().collect(summing), values.sum)
  }

}
