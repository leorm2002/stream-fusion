package fuse
import munit.FunSuite
import fuse.FusedStream
import fuse.FusedStream.collect
import fuse.FusedStream.skip
import fuse.FusedStream.limit
import fuse.FusedStream.filter
import fuse.FusedStream.map
import fuse.FusedStream.flatMap
import scala.annotation.static

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
      .collect(Collector.findFirst)

    assertEquals(found, Option(1))
  }

  test("Number find first from Array int") {
    val nums = Array(1, 2, 3, 4, 5)

    val found = FusedStream
      .from(nums)
      .collect(Collector.findFirst)

    assertEquals(found, Option(1))
  }
  test("Number find first from Array double") {
    val nums = Array(1.0, 2, 3, 4, 5)

    val found = FusedStream
      .from(nums)
      .collect(Collector.findFirst)

    assertEquals(found, Option(1d))
  }
  test("Number find first from Array string") {
    val nums = Array("1", "2", "3", "4", "5")

    val found = FusedStream
      .from(nums)
      .collect(Collector.findFirst)

    assertEquals(found, Option("1"))
  }

  test("Number find first from List") {
    val nums = List(1, 2, 3, 4, 5)

    val found = FusedStream
      .from(nums)
      .skip(2)
      .collect(Collector.findFirst)

    assertEquals(found, Option(3))
  }
  test("Number find first from Array int with skip") {
    val nums = Array(1, 2, 3, 4, 5)

    val found = FusedStream
      .from(nums)
      .skip(2)
      .collect(Collector.findFirst)

    assertEquals(found, Option(3))
  }

  test("Number find first from Array double with skip") {
    val nums = Array(1.0, 2.0, 3.0, 4.0, 5.0)

    val found = FusedStream
      .from(nums)
      .skip(2)
      .collect(Collector.findFirst)

    assertEquals(found, Option(3.0))
  }

  test("Number find first from Array string with skip") {
    val nums = Array("1", "2", "3", "4", "5")

    val found = FusedStream
      .from(nums)
      .skip(2)
      .collect(Collector.findFirst)

    assertEquals(found, Option("3"))
  }

  // --- 1. LIST INT ---
  test("Number find first from List Int") {
    val nums = List(1, 2, 3, 4, 5)

    val found = FusedStream
      .from(nums)
      .map(x => (x, x))
      .collect(Collector.findFirst)

    assertEquals(found, Option((1, 1)))
  }

  test("Number find first from List Int with skip") {
    val nums = List(1, 2, 3, 4, 5)

    val found = FusedStream
      .from(nums)
      .skip(2)
      .map(x => (x, x))
      .collect(Collector.findFirst)

    assertEquals(found, Option((3, 3)))
  }

  // --- 2. ARRAY INT ---
  test("Number find first from Array Int") {
    val nums = Array(1, 2, 3, 4, 5)

    val found = FusedStream
      .from(nums)
      .map(x => (x, x))
      .collect(Collector.findFirst)

    assertEquals(found, Option((1, 1)))
  }

  test("Number find first from Array Int with skip") {
    val nums = Array(1, 2, 3, 4, 5)

    val found = FusedStream
      .from(nums)
      .skip(2)
      .map(x => (x, x))
      .collect(Collector.findFirst)

    assertEquals(found, Option((3, 3)))
  }

  // --- 3. ARRAY DOUBLE ---
  test("Number find first from Array Double") {
    val nums = Array(1.0, 2.0, 3.0, 4.0, 5.0)

    val found = FusedStream
      .from(nums)
      .map(x => (x, x))
      .collect(Collector.findFirst)

    assertEquals(found, Option((1.0, 1.0)))
  }

  test("Number find first from Array Double with skip") {
    val nums = Array(1.0, 2.0, 3.0, 4.0, 5.0)

    val found = FusedStream
      .from(nums)
      .skip(2)
      .map(x => (x, x))
      .collect(Collector.findFirst)

    assertEquals(found, Option((3.0, 3.0)))
  }

  // --- 4. ARRAY STRING ---
  test("Number find first from Array String") {
    val nums = Array("1", "2", "3", "4", "5")

    val found = FusedStream
      .from(nums)
      .map(x => (x, x))
      .collect(Collector.findFirst)

    assertEquals(found, Option(("1", "1")))
  }

  test("Number find first from Array String with skip") {
    val nums = Array("1", "2", "3", "4", "5")

    val found = FusedStream
      .from(nums)
      .skip(2)
      .map(x => (x, x))
      .collect(Collector.findFirst)

    assertEquals(found, Option(("3", "3")))
  }
  // --- LIST INT ---
  test("Number find first from List Int with filter even") {
    val nums = List(1, 2, 3, 4, 5)

    val found = FusedStream
      .from(nums)
      .filter(_ % 2 == 0)
      .map(x => (x, x))
      .collect(Collector.findFirst)

    assertEquals(found, Option((2, 2)))
  }

  // --- ARRAY INT ---
  test("Number find first from Array Int with filter even") {
    val nums = Array(1, 2, 3, 4, 5)

    val found = FusedStream
      .from(nums)
      .filter(_ % 2 == 0)
      .map(x => (x, x))
      .collect(Collector.findFirst)

    assertEquals(found, Option((2, 2)))
  }

  // --- ARRAY DOUBLE ---
  test("Number find first from Array Double with filter even") {
    val nums = Array(1.0, 2.0, 3.0, 4.0, 5.0)

    val found = FusedStream
      .from(nums)
      .filter(_ % 2 == 0)
      .map(x => (x, x))
      .collect(Collector.findFirst)

    assertEquals(found, Option((2.0, 2.0)))
  }

  // --- ARRAY STRING ---
  test("Number find first from Array String with filter even") {
    val nums = Array("1", "2", "3", "4", "5")

    val found = FusedStream
      .from(nums)
      .filter(_.toIntOption.exists(_ % 2 == 0))
      .map(x => (x, x))
      .collect(Collector.findFirst)

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
      .collect(Collector.toList)

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
      .collect(Collector.toList)

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
      .collect(Collector.toList)

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
      .collect(Collector.toList)

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
      .collect(Collector.toList)

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
      .collect(Collector.toList)

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
      .collect(Collector.toList)

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

}
