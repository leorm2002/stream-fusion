package fuse.benchmarks

import fuse.{Collector, FusedStream}
import fuse.FusedStream.*
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole
import scala.concurrent.ExecutionContext.Implicits.global

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 8, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
class MapToListBenchmark {

  @Benchmark
  @OperationsPerInvocation(3)
  def fused(state: BenchmarkData, blackhole: Blackhole): Unit = {
    // 1. Strings: calcolo della lunghezza (primitivo) invece di concatenazione che alloca stringhe sull'heap
    val strings = state.javaStrings
    val stringLengths = FusedStream
      .from(strings)
      .map(s => s.length)
      .collect(Collector.toArray)
    blackhole.consume(stringLengths)

    // 2. People: estrazione del campo primitivo senza fare 'new Person'
    val people = state.javaPeople
    val ages = FusedStream
      .from(people)
      .map(p => p.age() + 1)
      .collect(Collector.toArray)
    blackhole.consume(ages)

    // 3. Points: normalizzazione logica senza istanziare nuovi Point
    val points = state.javaPoints
    val coordinates = FusedStream
      .from(points)
      .map(pt => if (pt.x() < 0 || pt.y() < 0) 0 else pt.x() + pt.y())
      .collect(Collector.toArray)
    blackhole.consume(coordinates)
  }

  @Benchmark
  @OperationsPerInvocation(3)
  def javaStream(state: BenchmarkData, blackhole: Blackhole): Unit = {
    val strings = state.javaStrings
    val stringLengths = strings
      .stream()
      .map(s => s.length)
      .toArray(new Array[Integer](_))
    blackhole.consume(stringLengths)

    val people = state.javaPeople
    val ages = people
      .stream()
      .map(p => p.age() + 1)
      .toArray(new Array[Integer](_))
    blackhole.consume(ages)

    val points = state.javaPoints
    val coordinates = points
      .stream()
      .map(pt => if (pt.x() < 0 || pt.y() < 0) 0 else pt.x() + pt.y())
      .toArray(new Array[Integer](_))
    blackhole.consume(coordinates)
  }

  @Benchmark
  @OperationsPerInvocation(3)
  def javaStreamOptimized(state: BenchmarkData, blackhole: Blackhole): Unit = {
    val strings = state.javaStrings
    val stringLengths: Array[Int] = strings
      .stream()
      .mapToInt(s => s.length())
      .toArray
    blackhole.consume(stringLengths)

    val people = state.javaPeople
    val ages: Array[Int] = people
      .stream()
      .mapToInt(p => p.age() + 1)
      .toArray
    blackhole.consume(ages)

    val points = state.javaPoints
    val coordinates: Array[Int] = points
      .stream()
      .mapToInt(pt => if (pt.x() < 0 || pt.y() < 0) 0 else pt.x() + pt.y())
      .toArray
    blackhole.consume(coordinates)
  }
  @Benchmark
  @OperationsPerInvocation(3)
  def scalaManual(state: BenchmarkData, blackhole: Blackhole): Unit = {
    // Strings
    val strings = state.scalaStrings
    val stringLengths = new Array[Int](strings.size)
    val stringIt = strings.iterator
    var i = 0
    while (stringIt.hasNext) {
      stringLengths(i) = stringIt.next().length
      i += 1
    }
    blackhole.consume(stringLengths)

    // People
    val people = state.scalaPeople
    val ages = new Array[Int](people.size)
    val peopleIt = people.iterator
    var j = 0
    while (peopleIt.hasNext) {
      ages(j) = peopleIt.next().age() + 1
      j += 1
    }
    blackhole.consume(ages)

    // Points
    val points = state.scalaPoints
    val coordinates = new Array[Int](points.size)
    val pointsIt = points.iterator
    var k = 0
    while (pointsIt.hasNext) {
      val pt = pointsIt.next()
      coordinates(k) = if (pt.x() < 0 || pt.y() < 0) 0 else pt.x() + pt.y()
      k += 1
    }
    blackhole.consume(coordinates)
  }

  @Benchmark
  @OperationsPerInvocation(3)
  def javaManual(state: BenchmarkData, blackhole: Blackhole): Unit = {
    // Strings (iteratore Java per evitare bounds-check duplicati ed overhead di get(i))
    val strings = state.javaStrings
    val stringLengths = new Array[Int](strings.size())
    val stringIt = strings.iterator()
    var i = 0
    while (stringIt.hasNext) {
      stringLengths(i) = stringIt.next().length
      i += 1
    }
    blackhole.consume(stringLengths)

    // People
    val people = state.javaPeople
    val ages = new Array[Int](people.size())
    val peopleIt = people.iterator()
    var j = 0
    while (peopleIt.hasNext) {
      ages(j) = peopleIt.next().age() + 1
      j += 1
    }
    blackhole.consume(ages)

    // Points
    val points = state.javaPoints
    val coordinates = new Array[Int](points.size())
    val pointsIt = points.iterator()
    var k = 0
    while (pointsIt.hasNext) {
      val pt = pointsIt.next()
      coordinates(k) = if (pt.x() < 0 || pt.y() < 0) 0 else pt.x() + pt.y()
      k += 1
    }
    blackhole.consume(coordinates)
  }

  @Benchmark
  @OperationsPerInvocation(3)
  def javaManualIndexed(state: BenchmarkData, blackhole: Blackhole): Unit = {
    // 1. Strings
    val strings = state.javaStrings.asInstanceOf[java.util.ArrayList[String]]
    val strLen = strings.size()
    val stringLengths = new Array[Int](strLen)
    var i = 0
    while (i < strLen) {
      stringLengths(i) = strings.get(i).length
      i += 1
    }
    blackhole.consume(stringLengths)

    // 2. People
    val people = state.javaPeople.asInstanceOf[java.util.ArrayList[Person]]
    val peopleLen = people.size()
    val ages = new Array[Int](peopleLen)
    var j = 0
    while (j < peopleLen) {
      ages(j) = people.get(j).age() + 1
      j += 1
    }
    blackhole.consume(ages)

    // 3. Points
    val points = state.javaPoints.asInstanceOf[java.util.ArrayList[Point]]
    val pointsLen = points.size()
    val coordinates = new Array[Int](pointsLen)
    var k = 0
    while (k < pointsLen) {
      val pt = points.get(k)
      coordinates(k) = if (pt.x() < 0 || pt.y() < 0) 0 else pt.x() + pt.y()
      k += 1
    }
    blackhole.consume(coordinates)
  }

  @Benchmark
  @OperationsPerInvocation(3)
  def scalaStream(state: BenchmarkData, blackhole: Blackhole): Unit = {
    import scala.jdk.CollectionConverters.*

    val strings = state.javaStrings
    // .asScala crea una vista su ArrayList, .view attiva il processing lazy
    val stringLengths: Array[Int] = strings.asScala.view
      .map(s => s.length)
      .toArray
    blackhole.consume(stringLengths)

    val people = state.javaPeople
    val ages: Array[Int] = people.asScala.view
      .map(p => p.age() + 1)
      .toArray
    blackhole.consume(ages)

    val points = state.javaPoints
    val coordinates: Array[Int] = points.asScala.view
      .map(pt => if (pt.x() < 0 || pt.y() < 0) 0 else pt.x() + pt.y())
      .toArray
    blackhole.consume(coordinates)
  }
}
