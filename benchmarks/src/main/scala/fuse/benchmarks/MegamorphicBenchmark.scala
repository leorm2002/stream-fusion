package fuse.benchmarks

import fuse.{Collector, CompileConfig, FusedStream}
import fuse.FusedStream.*
import java.util.ArrayList
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole
import scala.annotation.static
import scala.jdk.CollectionConverters.*

/** Benchmark 2: Scenario Megamorfico (Flusso applicativo reale su in-memory store)
  *
  * Simula un flusso di reportistica/analisi aziendale con 8 query eterogenee su un database in-memory rappresentato da un ArrayList di transazioni.
  */
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(3)
@State(Scope.Thread)
class MegamorphicBenchmark {
  import MegamorphicBenchmark.*

  @Benchmark
  @OperationsPerInvocation(8)
  def fusedStream(state: BenchmarkData, bh: Blackhole): Unit = {
    val txs = state.transactions
    bh.consume(fusedQ1(txs))
    bh.consume(fusedQ2(txs))
    bh.consume(fusedQ3(txs))
    bh.consume(fusedQ4(txs))
    bh.consume(fusedQ5(txs))
    bh.consume(fusedQ6(txs))
    bh.consume(fusedQ7(txs))
    bh.consume(fusedQ8(txs))
  }

  @Benchmark
  @OperationsPerInvocation(8)
  def fusedStreamSafe(state: BenchmarkData, bh: Blackhole): Unit = {
    val txs = state.transactions
    bh.consume(fusedSafeQ1(txs))
    bh.consume(fusedSafeQ2(txs))
    bh.consume(fusedSafeQ3(txs))
    bh.consume(fusedSafeQ4(txs))
    bh.consume(fusedSafeQ5(txs))
    bh.consume(fusedSafeQ6(txs))
    bh.consume(fusedSafeQ7(txs))
    bh.consume(fusedSafeQ8(txs))
  }

  @Benchmark
  @OperationsPerInvocation(8)
  def javaStream(state: BenchmarkData, bh: Blackhole): Unit = {
    val txs = state.transactions
    bh.consume(javaStreamQ1(txs))
    bh.consume(javaStreamQ2(txs))
    bh.consume(javaStreamQ3(txs))
    bh.consume(javaStreamQ4(txs))
    bh.consume(javaStreamQ5(txs))
    bh.consume(javaStreamQ6(txs))
    bh.consume(javaStreamQ7(txs))
    bh.consume(javaStreamQ8(txs))
  }

  @Benchmark
  @OperationsPerInvocation(8)
  def scalaStream(state: BenchmarkData, bh: Blackhole): Unit = {
    val txs = state.transactions
    bh.consume(scalaStreamQ1(txs))
    bh.consume(scalaStreamQ2(txs))
    bh.consume(scalaStreamQ3(txs))
    bh.consume(scalaStreamQ4(txs))
    bh.consume(scalaStreamQ5(txs))
    bh.consume(scalaStreamQ6(txs))
    bh.consume(scalaStreamQ7(txs))
    bh.consume(scalaStreamQ8(txs))
  }

  @Benchmark
  @OperationsPerInvocation(8)
  def javaManual(state: BenchmarkData, bh: Blackhole): Unit = {
    val txs = state.transactions
    bh.consume(javaManualQ1(txs))
    bh.consume(javaManualQ2(txs))
    bh.consume(javaManualQ3(txs))
    bh.consume(javaManualQ4(txs))
    bh.consume(javaManualQ5(txs))
    bh.consume(javaManualQ6(txs))
    bh.consume(javaManualQ7(txs))
    bh.consume(javaManualQ8(txs))
  }
}

object MegamorphicBenchmark {

  // =========================================================================
  // 1. FusedStream Queries (useUnsafe = true di default)
  // =========================================================================

  @static def fusedQ1(txs: ArrayList[Transaction]): Double =
    FusedStream
      .from(txs)
      .filter(t => t.status == 1 && t.category == "Electronics")
      .map(t => t.amount)
      .collect(summing)

  @static def fusedQ2(txs: ArrayList[Transaction]): Double =
    FusedStream
      .from(txs)
      .filter(t => t.category == "Groceries" && t.amount > 50.0)
      .map(t => t.amount * 0.05)
      .collect(summing)

  @static def fusedQ3(txs: ArrayList[Transaction]): Double =
    FusedStream
      .from(txs)
      .filter(t => t.status == 2 && t.userId < 200)
      .map(t => t.amount * 0.1)
      .collect(summing)

  @static def fusedQ4(txs: ArrayList[Transaction]): Double =
    FusedStream
      .from(txs)
      .filter(t => t.category == "Fashion" && t.amount >= 20.0 && t.amount <= 200.0)
      .map(t => t.amount * 0.22)
      .collect(summing)

  @static def fusedQ5(txs: ArrayList[Transaction]): Double =
    FusedStream
      .from(txs)
      .filter(t => t.status == 0 && (t.userId & 1) == 0)
      .map(t => t.amount)
      .collect(summing)

  @static def fusedQ6(txs: ArrayList[Transaction]): Double =
    FusedStream
      .from(txs)
      .filter(t => t.category == "Books" && t.amount < 30.0)
      .map(t => t.amount + 2.5)
      .collect(summing)

  @static def fusedQ7(txs: ArrayList[Transaction]): Double =
    FusedStream
      .from(txs)
      .filter(t => t.category == "Home" && t.status == 1 && t.amount > 150.0)
      .map(t => t.amount * 0.9)
      .collect(summing)

  @static def fusedQ8(txs: ArrayList[Transaction]): Double =
    FusedStream
      .from(txs)
      .filter(t => t.userId % 5 == 0)
      .map(t => t.amount * 1.05)
      .collect(summing)

  // =========================================================================
  // 2. FusedStreamSafe Queries (useUnsafe = false)
  // =========================================================================

  @static def fusedSafeQ1(txs: ArrayList[Transaction]): Double = {
    import SafeConfigs.safeCompileConfig
    FusedStream
      .from(txs)
      .filter(t => t.status == 1 && t.category == "Electronics")
      .map(t => t.amount)
      .collect(summing)
  }

  @static def fusedSafeQ2(txs: ArrayList[Transaction]): Double = {
    import SafeConfigs.safeCompileConfig
    FusedStream
      .from(txs)
      .filter(t => t.category == "Groceries" && t.amount > 50.0)
      .map(t => t.amount * 0.05)
      .collect(summing)
  }

  @static def fusedSafeQ3(txs: ArrayList[Transaction]): Double = {
    import SafeConfigs.safeCompileConfig
    FusedStream
      .from(txs)
      .filter(t => t.status == 2 && t.userId < 200)
      .map(t => t.amount * 0.1)
      .collect(summing)
  }

  @static def fusedSafeQ4(txs: ArrayList[Transaction]): Double = {
    import SafeConfigs.safeCompileConfig
    FusedStream
      .from(txs)
      .filter(t => t.category == "Fashion" && t.amount >= 20.0 && t.amount <= 200.0)
      .map(t => t.amount * 0.22)
      .collect(summing)
  }

  @static def fusedSafeQ5(txs: ArrayList[Transaction]): Double = {
    import SafeConfigs.safeCompileConfig
    FusedStream
      .from(txs)
      .filter(t => t.status == 0 && (t.userId & 1) == 0)
      .map(t => t.amount)
      .collect(summing)
  }

  @static def fusedSafeQ6(txs: ArrayList[Transaction]): Double = {
    import SafeConfigs.safeCompileConfig
    FusedStream
      .from(txs)
      .filter(t => t.category == "Books" && t.amount < 30.0)
      .map(t => t.amount + 2.5)
      .collect(summing)
  }

  @static def fusedSafeQ7(txs: ArrayList[Transaction]): Double = {
    import SafeConfigs.safeCompileConfig
    FusedStream
      .from(txs)
      .filter(t => t.category == "Home" && t.status == 1 && t.amount > 150.0)
      .map(t => t.amount * 0.9)
      .collect(summing)
  }

  @static def fusedSafeQ8(txs: ArrayList[Transaction]): Double = {
    import SafeConfigs.safeCompileConfig
    FusedStream
      .from(txs)
      .filter(t => t.userId % 5 == 0)
      .map(t => t.amount * 1.05)
      .collect(summing)
  }

  // =========================================================================
  // 3. JavaStream Queries
  // =========================================================================

  @static def javaStreamQ1(txs: ArrayList[Transaction]): Double =
    txs
      .stream()
      .filter(t => t.status() == 1 && "Electronics".equals(t.category()))
      .mapToDouble(t => t.amount())
      .sum()

  @static def javaStreamQ2(txs: ArrayList[Transaction]): Double =
    txs
      .stream()
      .filter(t => "Groceries".equals(t.category()) && t.amount() > 50.0)
      .mapToDouble(t => t.amount() * 0.05)
      .sum()

  @static def javaStreamQ3(txs: ArrayList[Transaction]): Double =
    txs
      .stream()
      .filter(t => t.status() == 2 && t.userId() < 200)
      .mapToDouble(t => t.amount() * 0.1)
      .sum()

  @static def javaStreamQ4(txs: ArrayList[Transaction]): Double =
    txs
      .stream()
      .filter(t => "Fashion".equals(t.category()) && t.amount() >= 20.0 && t.amount() <= 200.0)
      .mapToDouble(t => t.amount() * 0.22)
      .sum()

  @static def javaStreamQ5(txs: ArrayList[Transaction]): Double =
    txs
      .stream()
      .filter(t => t.status() == 0 && (t.userId() & 1) == 0)
      .mapToDouble(t => t.amount())
      .sum()

  @static def javaStreamQ6(txs: ArrayList[Transaction]): Double =
    txs
      .stream()
      .filter(t => "Books".equals(t.category()) && t.amount() < 30.0)
      .mapToDouble(t => t.amount() + 2.5)
      .sum()

  @static def javaStreamQ7(txs: ArrayList[Transaction]): Double =
    txs
      .stream()
      .filter(t => "Home".equals(t.category()) && t.status() == 1 && t.amount() > 150.0)
      .mapToDouble(t => t.amount() * 0.9)
      .sum()

  @static def javaStreamQ8(txs: ArrayList[Transaction]): Double =
    txs
      .stream()
      .filter(t => t.userId() % 5 == 0)
      .mapToDouble(t => t.amount() * 1.05)
      .sum()

  // =========================================================================
  // 4. ScalaStream Queries (Scala View)
  // =========================================================================

  @static def scalaStreamQ1(txs: ArrayList[Transaction]): Double =
    txs.asScala.view
      .filter(t => t.status == 1 && t.category == "Electronics")
      .map(t => t.amount)
      .sum

  @static def scalaStreamQ2(txs: ArrayList[Transaction]): Double =
    txs.asScala.view
      .filter(t => t.category == "Groceries" && t.amount > 50.0)
      .map(t => t.amount * 0.05)
      .sum

  @static def scalaStreamQ3(txs: ArrayList[Transaction]): Double =
    txs.asScala.view
      .filter(t => t.status == 2 && t.userId < 200)
      .map(t => t.amount * 0.1)
      .sum

  @static def scalaStreamQ4(txs: ArrayList[Transaction]): Double =
    txs.asScala.view
      .filter(t => t.category == "Fashion" && t.amount >= 20.0 && t.amount <= 200.0)
      .map(t => t.amount * 0.22)
      .sum

  @static def scalaStreamQ5(txs: ArrayList[Transaction]): Double =
    txs.asScala.view
      .filter(t => t.status == 0 && (t.userId & 1) == 0)
      .map(t => t.amount)
      .sum

  @static def scalaStreamQ6(txs: ArrayList[Transaction]): Double =
    txs.asScala.view
      .filter(t => t.category == "Books" && t.amount < 30.0)
      .map(t => t.amount + 2.5)
      .sum

  @static def scalaStreamQ7(txs: ArrayList[Transaction]): Double =
    txs.asScala.view
      .filter(t => t.category == "Home" && t.status == 1 && t.amount > 150.0)
      .map(t => t.amount * 0.9)
      .sum

  @static def scalaStreamQ8(txs: ArrayList[Transaction]): Double =
    txs.asScala.view
      .filter(t => t.userId % 5 == 0)
      .map(t => t.amount * 1.05)
      .sum

  // =========================================================================
  // 5. JavaManual Queries
  // =========================================================================

  @static def javaManualQ1(txs: ArrayList[Transaction]): Double = {
    val size = txs.size()
    var s = 0.0
    var i = 0
    while (i < size) {
      val t = txs.get(i)
      if (t.status == 1 && t.category == "Electronics") {
        s += t.amount
      }
      i += 1
    }
    s
  }

  @static def javaManualQ2(txs: ArrayList[Transaction]): Double = {
    val size = txs.size()
    var s = 0.0
    var i = 0
    while (i < size) {
      val t = txs.get(i)
      if (t.category == "Groceries" && t.amount > 50.0) {
        s += t.amount * 0.05
      }
      i += 1
    }
    s
  }

  @static def javaManualQ3(txs: ArrayList[Transaction]): Double = {
    val size = txs.size()
    var s = 0.0
    var i = 0
    while (i < size) {
      val t = txs.get(i)
      if (t.status == 2 && t.userId < 200) {
        s += t.amount * 0.1
      }
      i += 1
    }
    s
  }

  @static def javaManualQ4(txs: ArrayList[Transaction]): Double = {
    val size = txs.size()
    var s = 0.0
    var i = 0
    while (i < size) {
      val t = txs.get(i)
      if (t.category == "Fashion" && t.amount >= 20.0 && t.amount <= 200.0) {
        s += t.amount * 0.22
      }
      i += 1
    }
    s
  }

  @static def javaManualQ5(txs: ArrayList[Transaction]): Double = {
    val size = txs.size()
    var s = 0.0
    var i = 0
    while (i < size) {
      val t = txs.get(i)
      if (t.status == 0 && (t.userId & 1) == 0) {
        s += t.amount
      }
      i += 1
    }
    s
  }

  @static def javaManualQ6(txs: ArrayList[Transaction]): Double = {
    val size = txs.size()
    var s = 0.0
    var i = 0
    while (i < size) {
      val t = txs.get(i)
      if (t.category == "Books" && t.amount < 30.0) {
        s += t.amount + 2.5
      }
      i += 1
    }
    s
  }

  @static def javaManualQ7(txs: ArrayList[Transaction]): Double = {
    val size = txs.size()
    var s = 0.0
    var i = 0
    while (i < size) {
      val t = txs.get(i)
      if (t.category == "Home" && t.status == 1 && t.amount > 150.0) {
        s += t.amount * 0.9
      }
      i += 1
    }
    s
  }

  @static def javaManualQ8(txs: ArrayList[Transaction]): Double = {
    val size = txs.size()
    var s = 0.0
    var i = 0
    while (i < size) {
      val t = txs.get(i)
      if (t.userId % 5 == 0) {
        s += t.amount * 1.05
      }
      i += 1
    }
    s
  }
}
