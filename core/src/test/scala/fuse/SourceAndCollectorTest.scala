package fuse

import java.util.Arrays

import scala.collection.mutable.ListBuffer

import munit.FunSuite

import FusedStream.*
import RuntimeConfig.*
import fuse.Collector

/** Test end-to-end per le tipologie di sorgenti e i collector di FusedStream:
  * sorgenti Scala (List, Array) e Java (Iterable, ArrayList, LinkedList, accesso unsafe alla backing array),
  * collezioni esaustive e short-circuiting (toList, toSet, toArray, summing, findFirst, collector generici custom),
  * builder dinamici per tipi primitivi, preservazione dei valori nulli e conteggio corretto degli accessi agli iteratori.
  */
class SourceAndCollectorTest extends FunSuite {

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

    class EventCollector extends Collector[Int, ListBuffer[Int], String, Exhaustive] {
      inline def supplier(): ListBuffer[Int] = {
        events += "supplier"
        ListBuffer.empty[Int]
      }
      inline def accumulator(buffer: ListBuffer[Int], elem: Int): Boolean = {
        events += s"add:$elem"
        buffer += elem
        true
      }
      inline def finisher(buffer: ListBuffer[Int]): String = {
        events += "finisher"
        buffer.mkString(",")
      }
    }

    def collector(): EventCollector = {
      events += "collector"
      new EventCollector
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
    class SumCollector extends Collector[Int, ListBuffer[Int], Int, Exhaustive] {
      inline def supplier(): ListBuffer[Int] = ListBuffer.empty[Int]
      inline def accumulator(buffer: ListBuffer[Int], elem: Int): Boolean = {
        buffer += elem
        false
      }
      inline def finisher(buffer: ListBuffer[Int]): Int = buffer.sum
    }
    val result = FusedStream
      .from(Array.tabulate(3)(n => n + 1))
      .collect(new SumCollector)

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
    class TakeTwoCollector extends Collector[Int, ListBuffer[Int], List[Int], ShortCircuiting] {
      inline def supplier(): ListBuffer[Int] = ListBuffer.empty[Int]
      inline def accumulator(buffer: ListBuffer[Int], elem: Int): Boolean = {
        buffer += elem
        buffer.size == 2
      }
      inline def finisher(buffer: ListBuffer[Int]): List[Int] = buffer.toList
    }
    val collector = new TakeTwoCollector

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

}
