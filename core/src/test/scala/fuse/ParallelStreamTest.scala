package fuse

import java.util.concurrent.{ForkJoinPool, TimeUnit}
import java.util.concurrent.atomic.AtomicIntegerArray

import scala.compiletime.testing.typeCheckErrors
import scala.concurrent.ExecutionContext

import munit.FunSuite

import FusedStream.*
import RuntimeConfig.*
import fuse.Collector
import scala.collection.mutable.ListBuffer

/** Test end-to-end per gli stream paralleli di FusedStream:
  * esecuzione parallela con partizionamento dei dati, collector paralleli (toArray, summing),
  * rispetto dell'ordinamento della sorgente (Exact e Unknown), scalabilità con thread pool e worker multipli,
  * configurazione di chunking e chunksPerWorker, gestione degli overflow aritmetici,
  * propagazione degli errori nel runtime ed errori di tipo a compile-time (vincoli di parallelismo).
  */
class ParallelStreamTest extends FunSuite {

  test("Parallel map test") {
    val nums = Array(1, 2, 3, 4, 5)

    val found = FusedStream
      .from(nums)
      .parallel()
      .map(_ * 2)
      .collect(toArray)

    assertEquals(found.toSeq, Array(2, 4, 6, 8, 10).toSeq)
  }

  test("parallel can only be applied directly to a splittable source") {

    val errors = typeCheckErrors("""
    val nums = Array(1, 2, 3, 4, 5)

    FusedStream
      .from(nums)
      .map(_ * 2)
      .parallel()
      .collect(toArray)
  """)

    assert(errors.nonEmpty)
    assert(errors.exists(_.message.contains("parallel")))
  }

  test("Parallel stream cannot use an early-stopping collector") {

    val errors = typeCheckErrors("""
    val nums = Array(1, 2, 3, 4, 5)

    FusedStream
      .from(nums)
      .parallel()
      .map(_ * 2)
      .collect(findFirst)
  """)

    assert(errors.nonEmpty)
    assert(errors.exists(_.message.contains("ParallelCollector")))
  }

  test("flatMap inner stream cannot be parallel") {
    val errors = typeCheckErrors("""
    val xs = Array(1, 2, 3)
    val ys = Array(10, 20, 30)

    FusedStream
      .from(xs)
      .parallel()
      .flatMap { x =>
        FusedStream
          .from(ys)
          .parallel()
          .map(y => x + y)
      }
      .collect(toList)
  """)

    assert(errors.nonEmpty)
  }

  test("parallel cannot be used on non-splittable source") {
    val errors = typeCheckErrors("""
    val xs = List(1, 2, 3)

    FusedStream
      .from(xs)
      .parallel()
      .collect(toList)
  """)

    assert(errors.nonEmpty)
    assert(errors.exists(_.message.contains("parallel")))
  }

  test("parallel cannot be called twice") {
    val errors = typeCheckErrors("""
    val xs = Array(1, 2, 3)

    FusedStream
      .from(xs)
      .parallel()
      .parallel()
      .collect(toList)
  """)

    assert(errors.nonEmpty)
    assert(errors.exists(_.message.contains("parallel")))
  }

  test("parallel stream requires a combinable collector") {
    val errors = typeCheckErrors("""
    import scala.collection.mutable.ListBuffer

    val xs = Array(1, 2, 3)

    val collector =
      new Collector[
        Int,
        ListBuffer[Int],
        List[Int],
        NoEarlyStopping
      ] {

        override def supplier(): ListBuffer[Int] =
          ListBuffer.empty[Int]

        override def accumulator(
            buf: ListBuffer[Int],
            elem: Int
        ): Boolean = {
          buf += elem
          false
        }

        override def finisher(
            buf: ListBuffer[Int]
        ): List[Int] =
          buf.toList
      }

    FusedStream
      .from(xs)
      .parallel()
      .collect(collector)
  """)

    assert(errors.nonEmpty)
  }

  test("Parallel + Unknown collects flatMap results in source order") {
    given RuntimeConfig = RuntimeConfig.default.copy(workerCount = 2, chunksPerWorker = 1)
    val nums = Array(1, 2, 3)

    val result = FusedStream
      .from(nums)
      .parallel()
      .flatMap(n => FusedStream.from(Array(n, -n)))
      .collect(Collector.toArray)

    assertEquals(result.toList, List(1, -1, 2, -2, 3, -3))
  }

  test("Parallel + Exact collects mapped results at their source indexes") {
    given RuntimeConfig = RuntimeConfig.default.copy(workerCount = 2, chunksPerWorker = 1)
    val nums = Array(1, 2, 3, 4, 5)

    val result = FusedStream
      .from(nums)
      .parallel()
      .map(_ * 2)
      .collect(Collector.toArray)

    assertEquals(result.toList, List(2, 4, 6, 8, 10))
  }

  test("Parallel + UpperBound collects filtered results in source order") {
    given RuntimeConfig = RuntimeConfig.default.copy(workerCount = 2, chunksPerWorker = 2)
    inline given CompileConfig = new CompileConfig(useUnsafe = false, enableLogging = true)
    val nums = Array(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)

    val result = FusedStream
      .from(nums)
      .parallel()
      .filter(_ % 2 == 0)
      .collect(Collector.toArray)

    assertEquals(result.toList, List(2, 4, 6, 8, 10))
  }

  test("Parallel + UpperBound handles empty filter matches") {
    given RuntimeConfig = RuntimeConfig.default.copy(workerCount = 2, chunksPerWorker = 2)
    val nums = Array(1, 3, 5, 7)

    val result = FusedStream
      .from(nums)
      .parallel()
      .filter(_ % 2 == 0)
      .collect(Collector.toArray)

    assertEquals(result.toList, Nil)
  }

  test("Parallel stream collects toList with custom combinable collector across map and filter") {
    given RuntimeConfig = RuntimeConfig.default.copy(workerCount = 3, chunksPerWorker = 2)
    val nums = (1 to 50).toArray

    val customToList = new ParallelCollector[Int, ListBuffer[Int], List[Int]] {
      override def supplier(): ListBuffer[Int] = ListBuffer.empty[Int]
      override def accumulator(buf: ListBuffer[Int], elem: Int): Boolean = {
        buf.addOne(elem)
        false
      }
      override def combine(left: ListBuffer[Int], right: ListBuffer[Int]): ListBuffer[Int] = {
        left.addAll(right)
        left
      }
      override def finisher(buf: ListBuffer[Int]): List[Int] = buf.toList
    }

    val result = FusedStream
      .from(nums)
      .parallel()
      .map(_ * 3)
      .filter(_ % 2 == 0)
      .collect(customToList)

    val expected = nums.map(_ * 3).filter(_ % 2 == 0).toList
    assertEquals(result, expected)
  }

  test("Parallel stream collects empty array with custom combinable collector") {
    val nums = Array.empty[Int]
    val customToList = new ParallelCollector[Int, ListBuffer[Int], List[Int]] {
      override def supplier(): ListBuffer[Int] = ListBuffer.empty[Int]
      override def accumulator(buf: ListBuffer[Int], elem: Int): Boolean = {
        buf.addOne(elem)
        false
      }
      override def combine(left: ListBuffer[Int], right: ListBuffer[Int]): ListBuffer[Int] = {
        left.addAll(right)
        left
      }
      override def finisher(buf: ListBuffer[Int]): List[Int] = buf.toList
    }

    val result = FusedStream
      .from(nums)
      .parallel()
      .map(_ * 3)
      .filter(_ % 2 == 0)
      .collect(customToList)

    assertEquals(result, Nil)
  }

  for {
    size <- List(0, 1, 2, 37, 10003)
    workers <- List(1, 3, 16)
  } {
    test(s"parallel map/filter/sum visits $size elements once with $workers workers") {
      withRuntime(workers) { config =>
        given RuntimeConfig = config
        val values = Array.tabulate(size)(i => i)
        val visits = new AtomicIntegerArray(size)
        val result = FusedStream
          .from(values)
          .parallel()
          .map { value =>
            visits.incrementAndGet(value)
            value * 3 + 1
          }
          .filter(_ % 5 != 0)
          .collect(Collector.summing)

        assertEquals(result, values.iterator.map(_ * 3 + 1).filter(_ % 5 != 0).sum)
        assert((0 until size).forall(index => visits.get(index) == 1))
      }
    }
  }

  test("parallel sums read chunksPerWorker from the runtime configuration on each call") {
    withRuntime(2) { config =>
      // Rounding makes the partition boundaries observable without depending on timing.
      val values = Array(1e16, 1.0, -1e16, 1.0)
      def sum(chunksPerWorker: Int): Double = {
        given RuntimeConfig = config.copy(chunksPerWorker = chunksPerWorker)
        FusedStream.from(values).parallel().collect(Collector.summing)
      }

      assertEquals(sum(1), 0.0)
      assertEquals(sum(2), 1.0)
      assertEquals(sum(1), 0.0)
    }
  }

  test("parallel chunk reduction supports Long, Float and Double sums") {
    withRuntime(3) { config =>
      given RuntimeConfig = config
      val longs = Array.tabulate(103)(i => (i - 51).toLong * Int.MaxValue)
      val floats = Array.tabulate(103)(i => (i - 51).toFloat / 2)
      val doubles = Array.tabulate(103)(i => (i - 51).toDouble / 2)

      val longSum = FusedStream.from(longs).parallel().map(_ * 3).filter(_ > 0).collect(Collector.summing)
      val floatSum = FusedStream.from(floats).parallel().map(_ / 2).filter(_ > 0).collect(Collector.summing)
      val doubleSum = FusedStream.from(doubles).parallel().map(_ / 2).filter(_ > 0).collect(Collector.summing)

      assertEquals(longSum, longs.iterator.map(_ * 3).filter(_ > 0).sum)
      assertEquals(floatSum, floats.iterator.map(_ / 2).filter(_ > 0).sum)
      assertEquals(doubleSum, doubles.iterator.map(_ / 2).filter(_ > 0).sum)
    }
  }

  test("Int sums retain their overflow semantics") {
    withRuntime(3) { config =>
      given RuntimeConfig = config
      val values = Array.fill(103)(Int.MaxValue)
      assertEquals(FusedStream.from(values).parallel().collect(Collector.summing), values.sum)
    }
  }

  test("chunk boundary multiplication does not overflow Int") {
    withRuntime(4096) { config =>
      given RuntimeConfig = config
      // Many logical workers on a small executor exercise large boundary products
      // without allocating a huge array or thousands of physical threads.
      val values = Array.tabulate(131073)(i => i % 7)
      assertEquals(FusedStream.from(values).parallel().collect(Collector.summing), values.sum)
    }
  }

  test("failed pipelines propagate the cause and leave the executor reusable") {
    withRuntime(3) { config =>
      given RuntimeConfig = config
      val values = Array.tabulate(103)(i => i)
      val failure = new IllegalStateException("mapper failed")
      val thrown = intercept[IllegalStateException] {
        FusedStream
          .from(values)
          .parallel()
          .map { value =>
            if (value == 42) throw failure
            value
          }
          .collect(Collector.summing)
      }
      assert(thrown eq failure)
      assertEquals(FusedStream.from(values).parallel().collect(Collector.summing), values.sum)
    }
  }

  private def withRuntime(workers: Int)(check: RuntimeConfig => Unit): Unit = {
    val pool = new ForkJoinPool(math.min(workers, 4))
    try {
      check(RuntimeConfig(ExecutionContext.fromExecutor(pool), workers))
    } finally {
      pool.shutdown()
      assert(pool.awaitTermination(10, TimeUnit.SECONDS))
    }
  }

}
