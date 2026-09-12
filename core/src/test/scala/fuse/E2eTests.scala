package fuse
import munit.FunSuite
import FusedStream.*
import scala.annotation.static

import scala.compiletime.testing.typeCheckErrors
object E2eTests {
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
}
