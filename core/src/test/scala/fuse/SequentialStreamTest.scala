package fuse

import scala.annotation.static
import scala.compiletime.testing.typeCheckErrors
import scala.quoted.staging.*

import munit.FunSuite

import FusedStream.*

/** Test end-to-end per le operazioni sequenziali di FusedStream:
  * sorgenti (List, Array con Int, Double, String), trasformazioni (map, filter, flatMap),
  * operatori di slice (skip, limit), collezioni terminali (findFirst, toList)
  * e controlli statici di tipo sui metodi map e flatMap.
  */
object SequentialStreamTest {

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

class SequentialStreamTest extends FunSuite {

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

  test("Number find first from List with skip") {
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

  // --- 4. ARRAY STRING (inner map) ---
  test("flatMap infinite repeater from Array String with inner map and limits") {
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

  test("flatMap infinite repeater from Array String with static mappers and filter") {
    val nums = Array("a", "b", "c", "d", "e", "f")

    // This mappers and filters should emit invokestatic
    val result = FusedStream
      .from(nums)
      .flatMap(a => {
        FusedStream.from(InfiniteRepeater(a)).map(x => (a, x)).limit(4)
      })
      .filter(a => SequentialStreamTest.alwaysTrue(a, a))
      .limit(20)
      .map(SequentialStreamTest.myMapper)
      .map(SequentialStreamTest.myMapper2)
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
      .filter(a => SequentialStreamTest.alwaysTrue(a, a))
      .limit(20)
      .map(SequentialStreamTest.myMapper)
      .map(SequentialStreamTest.myMapper2)
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

  test("FusedStream.map function cannot return a Stream") {

    val errors = typeCheckErrors("""
      val nums = List(1, 2, 3, 4, 5)
      val found = FusedStream
        .from(nums)
        .map(x => FusedStream.of(x))
        .collect(findFirst)""")

    assert(errors.size == 1)
    assert(errors.head.message.contains("Use flatMap instead."))
  }

  test("FusedStream.flatMap function cannot contain a nested Stream") {

    val errors = typeCheckErrors("""
      val nums = List(1, 2, 3, 4, 5)
      val found = FusedStream
        .from(nums)
        .flatMap(x => FusedStream.of(FusedStream.of(x)))
        .collect(findFirst)""")

    assert(errors.size == 1)
    assert(errors.head.message.contains("cannot contain another Stream as an element"))
  }

}
