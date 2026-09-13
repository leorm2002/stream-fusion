package fuse

import java.util.Arrays
import java.util.concurrent.{ForkJoinPool, TimeUnit}
import java.util.concurrent.atomic.AtomicIntegerArray

import scala.annotation.static
import scala.collection.mutable.ListBuffer
import scala.compiletime.testing.typeCheckErrors
import scala.concurrent.ExecutionContext
import scala.quoted.staging.*

import munit.FunSuite

import FusedStream.*
import RuntimeConfig.*
import fuse.Collector
object E2eTests {

  given Compiler = Compiler.make(getClass.getClassLoader)

  @static
  def alwaysTrue[A](a: A, b: A): Boolean = {
    true
  }
  @static
  def myMapper[A](a: A) = {
    (a, a)
  }
  @static
  def myMapper2[A](a: (A, A)) = {
    a(0)
  }
}
class E2eTests extends FunSuite {

  test("Number find first from List") {
    val nums = List(1, 2, 3, 4, 5)

    val found = FusedStream
      .from(nums)
      .collect(findFirst)

    assertEquals(found, Option(1))
  }

  test("Number find first from Array int") {
    val nums = Array(1, 2, 3, 4, 5)

    val found = FusedStream
      .from(nums)
      .collect(findFirst)

    assertEquals(found, Option(1))
  }
  test("Number find first from Array double") {
    val nums = Array(1.0, 2, 3, 4, 5)

    val found = FusedStream
      .from(nums)
      .collect(findFirst)

    assertEquals(found, Option(1d))
  }
  test("Number find first from Array string") {
    val nums = Array("1", "2", "3", "4", "5")

    val found = FusedStream
      .from(nums)
      .collect(findFirst)

    assertEquals(found, Option("1"))
  }

  test("Number find first from List") {
    val nums = List(1, 2, 3, 4, 5)

    val found = FusedStream
      .from(nums)
      .skip(2)
      .collect(findFirst)

    assertEquals(found, Option(3))
  }
  test("Number find first from Array int with skip") {
    val nums = Array(1, 2, 3, 4, 5)

    val found = FusedStream
      .from(nums)
      .skip(2)
      .collect(findFirst)

    assertEquals(found, Option(3))
  }

  test("Number find first from Array double with skip") {
    val nums = Array(1.0, 2.0, 3.0, 4.0, 5.0)

    val found = FusedStream
      .from(nums)
      .skip(2)
      .collect(findFirst)

    assertEquals(found, Option(3.0))
  }

  test("Number find first from Array string with skip") {
    val nums = Array("1", "2", "3", "4", "5")

    val found = FusedStream
      .from(nums)
      .skip(2)
      .collect(findFirst)

    assertEquals(found, Option("3"))
  }

  // --- 1. LIST INT ---
  test("Number find first from List Int") {
    val nums = List(1, 2, 3, 4, 5)

    val found = FusedStream
      .from(nums)
      .map(x => (x, x))
      .collect(findFirst)

    assertEquals(found, Option((1, 1)))
  }

  test("Number find first from List Int with skip") {
    val nums = List(1, 2, 3, 4, 5)

    val found = FusedStream
      .from(nums)
      .skip(2)
      .map(x => (x, x))
      .collect(findFirst)

    assertEquals(found, Option((3, 3)))
  }

  // --- 2. ARRAY INT ---
  test("Number find first from Array Int") {
    val nums = Array(1, 2, 3, 4, 5)

    val found = FusedStream
      .from(nums)
      .map(x => (x, x))
      .collect(findFirst)

    assertEquals(found, Option((1, 1)))
  }

  test("Number find first from Array Int with skip") {
    val nums = Array(1, 2, 3, 4, 5)

    val found = FusedStream
      .from(nums)
      .skip(2)
      .map(x => (x, x))
      .collect(findFirst)

    assertEquals(found, Option((3, 3)))
  }

  // --- 3. ARRAY DOUBLE ---
  test("Number find first from Array Double") {
    val nums = Array(1.0, 2.0, 3.0, 4.0, 5.0)

    val found = FusedStream
      .from(nums)
      .map(x => (x, x))
      .collect(findFirst)

    assertEquals(found, Option((1.0, 1.0)))
  }

  test("Number find first from Array Double with skip") {
    val nums = Array(1.0, 2.0, 3.0, 4.0, 5.0)

    val found = FusedStream
      .from(nums)
      .skip(2)
      .map(x => (x, x))
      .collect(findFirst)

    assertEquals(found, Option((3.0, 3.0)))
  }

  // --- 4. ARRAY STRING ---
  test("Number find first from Array String") {
    val nums = Array("1", "2", "3", "4", "5")

    val found = FusedStream
      .from(nums)
      .map(x => (x, x))
      .collect(findFirst)

    assertEquals(found, Option(("1", "1")))
  }

  test("Number find first from Array String with skip") {
    val nums = Array("1", "2", "3", "4", "5")

    val found = FusedStream
      .from(nums)
      .skip(2)
      .map(x => (x, x))
      .collect(findFirst)

    assertEquals(found, Option(("3", "3")))
  }
  // --- LIST INT ---
  test("Number find first from List Int with filter even") {
    val nums = List(1, 2, 3, 4, 5)

    val found = FusedStream
      .from(nums)
      .filter(_ % 2 == 0)
      .map(x => (x, x))
      .collect(findFirst)

    assertEquals(found, Option((2, 2)))
  }

  // --- ARRAY INT ---
  test("Number find first from Array Int with filter even") {
    val nums = Array(1, 2, 3, 4, 5)

    val found = FusedStream
      .from(nums)
      .filter(_ % 2 == 0)
      .map(x => (x, x))
      .collect(findFirst)

    assertEquals(found, Option((2, 2)))
  }

  // --- ARRAY DOUBLE ---
  test("Number find first from Array Double with filter even") {
    val nums = Array(1.0, 2.0, 3.0, 4.0, 5.0)

    val found = FusedStream
      .from(nums)
      .filter(_ % 2 == 0)
      .map(x => (x, x))
      .collect(findFirst)

    assertEquals(found, Option((2.0, 2.0)))
  }

  // --- ARRAY STRING ---
  test("Number find first from Array String with filter even") {
    val nums = Array("1", "2", "3", "4", "5")

    val found = FusedStream
      .from(nums)
      .filter(_.toIntOption.exists(_ % 2 == 0))
      .map(x => (x, x))
      .collect(findFirst)

    assertEquals(found, Option(("2", "2")))
  }

  test("flatMap infinite repeater from List Int with limits and map") {
    val nums = List(1, 2)

    // Per ogni numero genera: "1", "11", "111", "1111" poi "2", "22", "222", "2222" ...
    // Con 2 elementi nella lista (2 * 4 = 8 elementi totali), limit(20) ne prenderà 8.
    val result = FusedStream
      .from(nums)
      .flatMap(a => FusedStream.from(InfiniteRepeater(a)).limit(4))
      .map(x => (x, x))
      .limit(20)
      .collect(toList)

    val expected = List(
      ("1", "1"),
      ("11", "11"),
      ("111", "111"),
      ("1111", "1111"),
      ("2", "2"),
      ("22", "22"),
      ("222", "222"),
      ("2222", "2222")
    )

    assertEquals(result, expected)
  }
  // TODO: ha un errore e non blocca gli infiniti

  // --- 2. ARRAY INT ---
  test("flatMap infinite repeater from Array Int with limits and map") {
    val nums = Array(1, 2, 3, 4, 5, 6)

    // 6 elementi * 4 ripetizioni = 24 elementi prodotti.
    // limit(20) ne taglierà esattamente 20!
    val result = FusedStream
      .from(nums)
      .flatMap(a => FusedStream.from(InfiniteRepeater(a)).limit(4))
      .map(x => (x, x))
      .limit(20)
      .collect(toList)

    assertEquals(result.size, 20)
    assertEquals(result.head, ("1", "1"))
    assertEquals(result(4), ("2", "2"))
    assertEquals(result.last, ("5555", "5555")) // Il 20° elemento è l'ultimo del '5'
  }

  // --- 3. ARRAY DOUBLE ---
  test("flatMap infinite repeater from Array Double with limits and map") {
    val nums = Array(1.0, 2.0, 3.0, 4.0, 5.0, 6.0)

    val result = FusedStream
      .from(nums)
      .flatMap(a => FusedStream.from(InfiniteRepeater(a)).limit(4))
      .map(x => (x, x))
      .limit(20)
      .collect(toList)

    assertEquals(result.size, 20)
    assertEquals(result.head, ("1.0", "1.0"))
    assertEquals(result(3), ("1.01.01.01.0", "1.01.01.01.0"))
  }

  // --- 4. ARRAY STRING ---
  test("flatMap infinite repeater from Array String with limits and map") {
    val nums = Array("a", "b", "c", "d", "e", "f")

    val result = FusedStream
      .from(nums)
      .flatMap(a => FusedStream.from(InfiniteRepeater(a)).limit(4))
      .map(x => (x, x))
      .limit(20)
      .collect(toList)

    val expectedFirst4 = List(
      ("a", "a"),
      ("aa", "aa"),
      ("aaa", "aaa"),
      ("aaaa", "aaaa")
    )

    assertEquals(result.size, 20)
    assertEquals(result.take(4), expectedFirst4)
    assertEquals(result.last, ("eeee", "eeee"))
  }

  // --- 4. ARRAY STRING ---
  test("flatMap infinite repeater from Array String with limits and map") {
    val nums = Array("a", "b", "c", "d", "e", "f")

    val result = FusedStream
      .from(nums)
      .flatMap(a => FusedStream.from(InfiniteRepeater(a)).map(x => (a, x)).limit(4))
      .limit(20)
      .collect(toList)

    val expectedFirst4 = List(
      ("a", "a"),
      ("a", "aa"),
      ("a", "aaa"),
      ("a", "aaaa")
    )

    assertEquals(result.size, 20)
    assertEquals(result.take(4), expectedFirst4)
    assertEquals(result.last, ("e", "eeee"))
  }

  test("flatMap infinite repeater from Array String with limits and map") {
    val nums = Array("a", "b", "c", "d", "e", "f")

    // This mappers and filters should emit invokestatic
    val result = FusedStream
      .from(nums)
      .flatMap(a => {
        FusedStream.from(InfiniteRepeater(a)).map(x => (a, x)).limit(4)
      })
      .filter(a => E2eTests.alwaysTrue(a, a))
      .limit(20)
      .map(E2eTests.myMapper)
      .map(E2eTests.myMapper2)
      .collect(toList)

    val expectedFirst4 = List(
      ("a", "a"),
      ("a", "aa"),
      ("a", "aaa"),
      ("a", "aaaa")
    )

    assertEquals(result.size, 20)
    assertEquals(result.take(4), expectedFirst4)
    assertEquals(result.last, ("e", "eeee"))
  }

  test("flatMap infinite repeater from Array String with limits and map and nested flatmap") {
    val nums = Array("a", "b", "c", "d", "e", "f")

    // This mappers and filters should emit invokestatic
    val result = FusedStream
      .from(nums)
      .flatMap(a => {
        FusedStream
          .from(InfiniteRepeater(a))
          .map(x => (a, x))
          .limit(4)
          .flatMap(t => FusedStream.from(InfiniteRepeater(t)).limit(2))
          .map(x => (a, x))
      })
      .filter(a => E2eTests.alwaysTrue(a, a))
      .limit(20)
      .map(E2eTests.myMapper)
      .map(E2eTests.myMapper2)
      .collect(toList)
    val expectedFirst4 = List(
      ("a", "(a,a)"),
      ("a", "(a,a)(a,a)"),
      ("a", "(a,aa)"),
      ("a", "(a,aa)(a,aa)")
    )

    assertEquals(result.size, 20)
    assertEquals(result.take(4), expectedFirst4)
    assertEquals(result.last, ("c", "(c,cc)(c,cc)"))
  }

  test("Block declarations before skip are preserved") {
    val nums = List(1, 2, 3, 4, 5)

    val found = ({
      val source = nums
      FusedStream.from(source)
    })
      .skip(2)
      .collect(findFirst)

    assertEquals(found, Option(3))
  }

  test("Skip accepts runtime computed value") {
    val nums = List(1, 2, 3, 4, 5)

    def elementsToSkip(): Int = 2

    val found = FusedStream
      .from(nums)
      .skip(elementsToSkip())
      .collect(findFirst)

    assertEquals(found, Option(3))
  }

  test("Skip count expression is evaluated only once") {
    val nums = List(1, 2, 3, 4, 5)

    var calls = 0

    def elementsToSkip(): Int = {
      calls += 1
      2
    }

    val result = FusedStream
      .from(nums)
      .skip(elementsToSkip())
      .collect(toList)

    assertEquals(result, List(3, 4, 5))
    assertEquals(calls, 1)
  }

  test("Limit count expression is evaluated only once") {
    val nums = List(1, 2, 3, 4, 5)

    var calls = 0

    def elementsToLimit(): Int = {
      calls += 1
      2
    }

    val result = FusedStream
      .from(nums)
      .limit(elementsToLimit())
      .collect(toList)

    assertEquals(result, List(1, 2))
    assertEquals(calls, 1)
  }
  test("Map fusion evaluates first mapper exactly once") {
    val nums = List(1, 2, 3)

    var calls = 0

    val result = FusedStream
      .from(nums)
      .map(x => {
        calls += 1
        x * 2
      })
      .map(x => x + x)
      .collect(toList)

    assertEquals(result, List(4, 8, 12))
    assertEquals(calls, 3)
  }
  test("Map fusion does not remove evaluation of previous mapper") {
    val nums = List(1, 2, 3)

    var calls = 0

    val result = FusedStream
      .from(nums)
      .map(x => {
        calls += 1
        x * 2
      })
      .map(_ => 42)
      .collect(toList)

    assertEquals(result, List(42, 42, 42))
    assertEquals(calls, 3)
  }
  test("Map function expression is evaluated only once") {
    val nums = List(1, 2, 3)

    var calls = 0

    def mapper(): Int => Int = {
      calls += 1
      _ * 2
    }

    val result = FusedStream
      .from(nums)
      .map(mapper())
      .collect(toList)

    assertEquals(result, List(2, 4, 6))
    assertEquals(calls, 1)
  }

  test("Filter predicate expression is evaluated only once") {
    val nums = List(1, 2, 3, 4)

    var calls = 0

    def makePredicate(): Int => Boolean = {
      calls += 1
      x => x % 2 == 0
    }

    val result = FusedStream
      .from(nums)
      .filter(makePredicate())
      .collect(toList)

    assertEquals(result, List(2, 4))
    assertEquals(calls, 1)
  }
  test("Map fusion evaluates mapper expressions only once") {
    val nums = List(1, 2, 3)

    var mapper1Calls = 0
    var mapper2Calls = 0

    def mapper1(): Int => Int = {
      mapper1Calls += 1
      _ * 2
    }

    def mapper2(): Int => Int = {
      mapper2Calls += 1
      _ + 1
    }

    val result = FusedStream
      .from(nums)
      .map(mapper1())
      .map(mapper2())
      .collect(toList)

    assertEquals(result, List(3, 5, 7))
    assertEquals(mapper1Calls, 1)
    assertEquals(mapper2Calls, 1)
  }
  test("Filter fusion evaluates predicate expressions only once") {
    val nums = List(1, 2, 3, 4)

    var predicate1Calls = 0
    var predicate2Calls = 0

    def predicate1(): Int => Boolean = {
      predicate1Calls += 1
      _ > 1
    }

    def predicate2(): Int => Boolean = {
      predicate2Calls += 1
      _ % 2 == 0
    }

    val result = FusedStream
      .from(nums)
      .filter(predicate1())
      .filter(predicate2())
      .collect(toList)

    assertEquals(result, List(2, 4))
    assertEquals(predicate1Calls, 1)
    assertEquals(predicate2Calls, 1)
  }

  test("Slice correctly handles from and until bounds") {
    val nums = List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)

    // from only: [3, +∞)
    val fromOnly = FusedStream
      .from(nums)
      .skip(3)
      .collect(toList)

    assertEquals(
      fromOnly,
      List(4, 5, 6, 7, 8, 9, 10)
    )

    // until only: [0, 4)
    val untilOnly = FusedStream
      .from(nums)
      .limit(4)
      .collect(toList)

    assertEquals(
      untilOnly,
      List(1, 2, 3, 4)
    )

    // from + until: [3, 7)
    val fromAndUntil = FusedStream
      .from(nums)
      .skip(3)
      .limit(4)
      .collect(toList)

    assertEquals(
      fromAndUntil,
      List(4, 5, 6, 7)
    )
  }

  test("Parallel map test ") {
    val nums = Array(1, 2, 3, 4, 5)

    val found = FusedStream
      .from(nums)
      .parallel()
      .map(_ * 2)
      .collect(toArray)

    assertEquals(found.toSeq, Array(2, 4, 6, 8, 10).toSeq)
  }

  import scala.compiletime.testing.typeCheckErrors

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
  import scala.compiletime.testing.typeCheckErrors

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
  private def javaLists(values: Int*): List[java.util.List[Int]] = {
    val arrayList = new java.util.ArrayList[Int]()
    val linkedList = new java.util.LinkedList[Int]()
    values.foreach { value =>
      arrayList.add(value)
      linkedList.add(value)
    }
    Arrays.asList()
    List(arrayList, linkedList)
  }

  private final class CountingIterable(values: List[Int]) extends Iterable[Int] {
    var iteratorCalls = 0
    var hasNextCalls = 0
    var nextCalls = 0

    override def iterator: Iterator[Int] = {
      iteratorCalls += 1
      val underlying = values.iterator
      new Iterator[Int] {
        override def hasNext: Boolean = {
          hasNextCalls += 1
          underlying.hasNext
        }
        override def next(): Int = {
          nextCalls += 1
          underlying.next()
        }
      }
    }
  }

  private def asJavaIterable(source: CountingIterable): java.lang.Iterable[Int] = new java.lang.Iterable[Int] {
    override def iterator(): java.util.Iterator[Int] = {
      val underlying = source.iterator
      new java.util.Iterator[Int] {
        override def hasNext(): Boolean = underlying.hasNext
        override def next(): Int = underlying.next()
      }
    }
  }

  test("Scala iterable sources support generic and specialized collectors") {
    val values = List(1, 2, 3, 4, 5)
    val expected = values.filter(_ % 2 == 0).map(_ * 3)

    assertEquals(FusedStream.from(values).filter(_ % 2 == 0).map(_ * 3).collect(Collector.toList), expected)
    assertEquals(FusedStream.from(values).filter(_ % 2 == 0).map(_ * 3).collect(Collector.toSet), expected.toSet)
    assertEquals(FusedStream.from(values).filter(_ % 2 == 0).map(_ * 3).collect(Collector.toArray).toList, expected)
    assertEquals(FusedStream.from(values).filter(_ % 2 == 0).map(_ * 3).collect(Collector.summing), expected.sum)
    assertEquals(FusedStream.of("single").collect(Collector.toArray).toList, List("single"))
  }

  test("Dynamic builders grow and return arrays with the concrete primitive element type") {
    val values = (0 until 1024).toList
    val longs = FusedStream.from(values).map(_.toLong * 10).collect(Collector.toArray)
    val booleans = FusedStream.from(values).map(_ % 2 == 0).collect(Collector.toArray)
    val empty = FusedStream.from(List.empty[Double]).collect(Collector.toArray)

    assertEquals(longs.toList, values.map(_.toLong * 10))
    assertEquals(booleans.toList, values.map(_ % 2 == 0))
    assertEquals(empty.toList, List.empty[Double])
  }

  test("Array collection preserves null values with partial, full and empty filter results") {
    val values = Array[String](null, "keep", null, "discard")
    val partial = FusedStream.from(values).filter(value => value == null || value == "keep").collect(Collector.toArray)
    val full = FusedStream.from(values).filter(_ => true).collect(Collector.toArray)
    val empty = FusedStream.from(values).filter(_ => false).collect(Collector.toArray)

    assertEquals(partial.toList, List[String](null, "keep", null))
    assertEquals(full.toList, values.toList)
    assertEquals(empty.toList, List.empty[String])
  }

  test("Java iterable sources consume each element once across map and filter") {
    val counted = new CountingIterable(List(1, 2, 3, 4, 5))
    val source = asJavaIterable(counted)
    var mapCalls = 0

    val result = FusedStream
      .from(source)
      .map { n =>
        mapCalls += 1
        n * 2
      }
      .filter(_ > 4)
      .collect(Collector.toArray)

    assertEquals(result.toList, List(6, 8, 10))
    assertEquals(counted.iteratorCalls, 1)
    assertEquals(counted.nextCalls, 5)
    assertEquals(mapCalls, 5)
  }

  test("ArrayList and LinkedList sources preserve indexes and filtered output") {
    javaLists(1, 2, 3, 4, 5).foreach { source =>
      assertEquals(FusedStream.from(source).map(_.toString).collect(Collector.toArray).toList, List("1", "2", "3", "4", "5"))
      assertEquals(FusedStream.from(source).filter(_ % 2 == 0).map(_ * 2).collect(Collector.toArray).toList, List(4, 8))
      assertEquals(FusedStream.from(source).skip(1).limit(3).collect(Collector.summing), 9)
      assertEquals(FusedStream.from(source).filter(_ > 2).collect(Collector.toList), List(3, 4, 5))
      assertEquals(FusedStream.from(source).filter(_ > 2).collect(Collector.findFirst[Int]), Some(3))
    }
  }

  test("Generic collectors initialize and finish once and only stop when marked EarlyStopping") {
    val events = ListBuffer.empty[String]
    val values = Array(1, 2, 3)

    def collector(): Collector[Int, ListBuffer[Int], String, Exhaustive] = {
      events += "collector"
      new Collector[Int, ListBuffer[Int], String, Exhaustive] {
        override def supplier(): ListBuffer[Int] = {
          events += "supplier"
          ListBuffer.empty[Int]
        }
        override def accumulator(buffer: ListBuffer[Int], elem: Int): Boolean = {
          events += s"add:$elem"
          buffer += elem
          true
        }
        override def finisher(buffer: ListBuffer[Int]): String = {
          events += "finisher"
          buffer.mkString(",")
        }
      }
    }

    assertEquals(FusedStream.from(values).collect(collector()), "1,2,3")
    assertEquals(events.toList, List("collector", "supplier", "add:1", "add:2", "add:3", "finisher"))

    events.clear()
    assertEquals(FusedStream.from(Array.empty[Int]).collect(collector()), "")
    assertEquals(events.toList, List("collector", "supplier", "finisher"))
  }

  test("findFirst stops Scala and Java iterators before another hasNext call") {
    val scalaSource = new CountingIterable(List(1, 2, 3, 4))
    val javaCounted = new CountingIterable(List(1, 2, 3, 4))
    val javaSource = asJavaIterable(javaCounted)

    assertEquals(FusedStream.from(scalaSource).filter(_ % 2 == 0).collect(Collector.findFirst[Int]), Some(2))
    assertEquals(FusedStream.from(javaSource).filter(_ % 2 == 0).collect(Collector.findFirst[Int]), Some(2))
    List(scalaSource, javaCounted).foreach { source =>
      assertEquals(source.iteratorCalls, 1)
      assertEquals(source.nextCalls, 2)
      assertEquals(source.hasNextCalls, 2)
    }
  }

  test("Generated declarations retain definitions in inline sources and collectors") {
    val result = FusedStream
      .from(Array.tabulate(3)(n => n + 1))
      .collect(new Collector[Int, ListBuffer[Int], Int, Exhaustive] {
        override def supplier(): ListBuffer[Int] = ListBuffer.empty[Int]
        override def accumulator(buffer: ListBuffer[Int], elem: Int): Boolean = {
          buffer += elem
          false
        }
        override def finisher(buffer: ListBuffer[Int]): Int = buffer.sum
      })

    assertEquals(result, 6)
  }

  test("Zero limits do not inspect or consume iterator elements") {
    val scalaSource = new CountingIterable(List(1, 2, 3))
    val javaCounted = new CountingIterable(List(1, 2, 3))
    val javaSource = asJavaIterable(javaCounted)

    assertEquals(FusedStream.from(scalaSource).limit(0).collect(Collector.toArray).toList, Nil)
    assertEquals(FusedStream.from(javaSource).limit(0).collect(Collector.toList), Nil)
    List(scalaSource, javaCounted).foreach { source =>
      assertEquals(source.nextCalls, 0)
      assertEquals(source.hasNextCalls, 0)
    }
  }

  test("Slice bounds are evaluated once and count elements at their pipeline position") {
    val values = Array(1, 2, 3, 4, 5, 6, 7, 8)
    var skipCalls = 0
    var limitCalls = 0
    def skipCount(): Int = { skipCalls += 1; 1 }
    def limitCount(): Int = { limitCalls += 1; 2 }

    val result = FusedStream.from(values).skip(skipCount()).filter(_ % 2 == 0).limit(limitCount()).skip(1).collect(Collector.toArray)

    assertEquals(result.toList, List(4))
    assertEquals(skipCalls, 1)
    assertEquals(limitCalls, 1)
    assertEquals(FusedStream.from(values).skip(100).limit(1).collect(Collector.toList), Nil)
    assertEquals(FusedStream.from(values).limit(2).collect(Collector.toArray).toList, List(1, 2))
  }

  test("Maps execute once per entering element, including elements later skipped") {
    val values = Array(1, 2, 3, 4, 5)
    val visited = ListBuffer.empty[Int]

    val result = FusedStream
      .from(values)
      .map { n =>
        visited += n
        n * 2
      }
      .skip(1)
      .filter(_ > 4)
      .limit(2)
      .collect(Collector.toArray)

    assertEquals(result.toList, List(6, 8))
    assertEquals(visited.toList, List(1, 2, 3, 4))
  }

  test("FlatMap initializes inner declarations and slice counters for every outer element") {
    val values = Array(1, 2, 3)
    var innerInitializations = 0
    val result = FusedStream
      .from(values)
      .flatMap { n =>
        val offset = n * 10
        innerInitializations += 1
        FusedStream.from(Array(offset, offset + 1, offset + 2, offset + 3)).skip(1).limit(2)
      }
      .collect(Collector.toArray)

    assertEquals(result.toList, List(11, 12, 21, 22, 31, 32))
    assertEquals(innerInitializations, 3)
  }

  test("Nested flatMaps preserve outer bindings in both Java list branches") {
    javaLists(1, 2).foreach { source =>
      val result = FusedStream
        .from(source)
        .flatMap { n =>
          FusedStream.from(List(n, n + 10)).flatMap { m =>
            FusedStream.from(Array(m, m * 2)).map(_ + n)
          }
        }
        .collect(Collector.toArray)

      assertEquals(result.toList, List(2, 3, 12, 23, 4, 6, 14, 26))
    }
  }

  test("Inner limits reset while an outer limit stops infinite sources") {
    val values = Array(1, 2, 3)
    val result = FusedStream
      .from(values)
      .flatMap { n =>
        FusedStream.from(InfiniteRepeater(n)).skip(1).limit(2)
      }
      .limit(3)
      .collect(Collector.toList)

    assertEquals(result, List("11", "111", "22"))
  }

  test("Limits before flatMap constrain the outer source without truncating inner streams") {
    val values = Array(1, 2, 3)
    val result = FusedStream
      .from(values)
      .limit(1)
      .flatMap { n =>
        FusedStream.from(Array(n, n + 10))
      }
      .collect(Collector.summing)

    assertEquals(result, 12)
  }

  test("An early stopping collector stops both the inner and outer iterators") {
    val outer = new CountingIterable(List(1, 2, 3))
    val inner = new CountingIterable(List(10, 20, 30))
    val collector = new Collector[Int, ListBuffer[Int], List[Int], ShortCircuiting] {
      override def supplier(): ListBuffer[Int] = ListBuffer.empty[Int]
      override def accumulator(buffer: ListBuffer[Int], elem: Int): Boolean = {
        buffer += elem
        buffer.size == 2
      }
      override def finisher(buffer: ListBuffer[Int]): List[Int] = buffer.toList
    }

    val result = FusedStream.from(outer).flatMap(n => FusedStream.from(inner).map(_ + n)).collect(collector)

    assertEquals(result, List(11, 21))
    assertEquals(outer.nextCalls, 1)
    assertEquals(outer.hasNextCalls, 1)
    assertEquals(inner.nextCalls, 2)
    assertEquals(inner.hasNextCalls, 2)
  }

  test("Empty iterable and Java list sources produce empty collections and sums") {
    val scalaSource = List.empty[Int]
    val javaSource = asJavaIterable(new CountingIterable(Nil))

    assertEquals(FusedStream.from(scalaSource).collect(Collector.toArray).toList, Nil)
    assertEquals(FusedStream.from(javaSource).collect(Collector.summing), 0)
    assertEquals(FusedStream.from(scalaSource).collect(Collector.findFirst[Int]), None)
    javaLists().foreach { source =>
      assertEquals(FusedStream.from(source).collect(Collector.toArray).toList, Nil)
      assertEquals(FusedStream.from(source).collect(Collector.summing), 0)
      assertEquals(FusedStream.from(source).collect(Collector.findFirst[Int]), None)
    }
  }

  test("Unsafe ArrayList access reads the backing array only up to the logical size") {
    inline given CompileConfig = CompileConfig(useUnsafe = true, enableLogging = false)
    val source = new java.util.ArrayList[Int](20) {
      override def get(index: Int): Int = throw new AssertionError("Unsafe access must read the backing array")
    }
    List(1, 2, 3, 4, 5).foreach(source.add)

    assertEquals(FusedStream.from(source).map(_ * 2).collect(Collector.toArray).toList, List(2, 4, 6, 8, 10))
    assertEquals(FusedStream.from(source).filter(_ % 2 == 0).collect(Collector.summing), 6)
    assertEquals(FusedStream.from(source).skip(1).collect(Collector.findFirst[Int]), Some(2))
  }

  test("Unsafe configuration keeps the iterator fallback and flatMap bindings for other Java lists") {
    inline given CompileConfig = CompileConfig(useUnsafe = true, enableLogging = false)
    javaLists(1, 2).foreach { source =>
      val result = FusedStream.from(source).flatMap(n => FusedStream.from(Array(n, n + 10)).limit(1)).collect(Collector.toArray)
      assertEquals(result.toList, List(1, 2))
    }
  }

  test("Mapped and filtered arrays can be summed") {
    val nums = Array(1, 2, 3, 4, 5)

    val result = FusedStream
      .from(nums)
      .map(_ * 2)
      .filter(_ > 4)
      .collect(Collector.summing)

    assertEquals(result, nums.map(_ * 2).filter(_ > 4).sum)
  }

  test("Summing preserves Long, Float and Double results") {
    val longs = Array(1L, 2L, 3L)
    val floats = Array(1.5f, 2.5f, 3.5f)
    val doubles = Array(1.5d, 2.5d, 3.5d)

    assertEquals(FusedStream.from(longs).collect(Collector.summing), longs.sum)
    assertEquals(FusedStream.from(floats).collect(Collector.summing), floats.sum)
    assertEquals(FusedStream.from(doubles).collect(Collector.summing), doubles.sum)
  }

  test("Mapped arrays preserve source indexes when collected to an array") {
    val nums = Array(1, 2, 3, 4, 5)

    val result = FusedStream
      .from(nums)
      .map(n => s"value=$n")
      .collect(Collector.toArray)

    assertEquals(result.toList, nums.map(n => s"value=$n").toList)
  }

  test("Array collection preserves source and function declarations") {
    var sourceCalls = 0
    var mapperCalls = 0
    var predicateCalls = 0

    def source(): Array[Int] = {
      sourceCalls += 1
      Array(1, 2, 3, 4, 5)
    }

    def mapper(): Int => Int = {
      mapperCalls += 1
      n => n * 2
    }

    def predicate(): Int => Boolean = {
      predicateCalls += 1
      n => n > 4
    }

    val result = FusedStream
      .from(source())
      .map(mapper())
      .filter(predicate())
      .collect(Collector.toArray)

    assertEquals(result.toList, List(6, 8, 10))
    assertEquals(sourceCalls, 1)
    assertEquals(mapperCalls, 1)
    assertEquals(predicateCalls, 1)
  }

  test("Empty arrays and filters without matches produce empty results") {
    val empty = Array.empty[Int]
    val nums = Array(1, 2, 3)

    assertEquals(FusedStream.from(empty).collect(Collector.summing), 0)
    assertEquals(FusedStream.from(empty).collect(Collector.toArray).toList, List.empty[Int])
    assertEquals(FusedStream.from(nums).filter(_ > 10).collect(Collector.toArray).toList, List.empty[Int])
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

  test("Sequential Exact non aligned index") {
    given RuntimeConfig = RuntimeConfig.default.copy(workerCount = 2, chunksPerWorker = 1)
    val nums = List(1, 2, 3, 4, 5)

    val result = FusedStream
      .from(nums)
      .skip(0)
      .map(_ * 2)
      .collect(Collector.toArray)

    assertEquals(result.toList, List(2, 4, 6, 8, 10))
  }

  test("toSet minimal test") {
    given RuntimeConfig = RuntimeConfig.default.copy(workerCount = 2, chunksPerWorker = 1)
    val nums = List(1, 2, 4, 3, 4, 5)

    val result = FusedStream
      .from(nums)
      .skip(0)
      .collect(toSet)

    assertEquals(result, Set(1, 2, 3, 4, 5))
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
