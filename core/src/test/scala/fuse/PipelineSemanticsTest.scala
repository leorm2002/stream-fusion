package fuse

import munit.FunSuite

import FusedStream.*

/** Test end-to-end per la semantica e le proprietà di fusione della pipeline:
  * preservazione delle dichiarazioni a blocco prima di skip,
  * valutazione singola delle espressioni di skip, limit, mapper e predicati,
  * correttezza della fusione di mappe e filtri successivi,
  * e gestione dei limiti di slice (from/until).
  */
class PipelineSemanticsTest extends FunSuite {

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

}
