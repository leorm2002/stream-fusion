package fuse.benchmarks

import fuse.{Collector, FusedStream}
import fuse.FusedStream.*
import fuse.internal.ArrayListAccessor
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole
import scala.jdk.CollectionConverters.*

/** Benchmark 1: Scenario Monomorfico ("Come non fare benchmark")
  * 
  * Un microbenchmark sintetico con un'unica pipeline (map + sum) e un singolo call site.
  * In questo scenario ideale, il compilatore JIT (HotSpot C2) osserva un target monomorfico,
  * applica l'inlining aggressivo anche all'infrastruttura di Java Stream, creando l'illusione
  * che Java Stream sia a "costo zero" e competitivo con un loop manuale.
  */
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@State(Scope.Thread)
class MonomorphicBenchmark {

  @Benchmark
  def fusedStream(state: BenchmarkData, bh: Blackhole): Unit = {
    val txs = state.transactions
    val sum = FusedStream
      .from(txs)
      .map(t => t.amount)
      .collect(summing)
    bh.consume(sum)
  }

  @Benchmark
  def javaStream(state: BenchmarkData, bh: Blackhole): Unit = {
    val txs = state.transactions
    val sum = txs
      .stream()
      .mapToDouble(t => t.amount)
      .sum()
    bh.consume(sum)
  }

  @Benchmark
  def scalaStream(state: BenchmarkData, bh: Blackhole): Unit = {
    val txs = state.transactions
    val sum = txs.asScala.view
      .map(t => t.amount)
      .sum
    bh.consume(sum)
  }

  @Benchmark
  def javaManual(state: BenchmarkData, bh: Blackhole): Unit = {
    val txs = state.transactions
    val size = txs.size()
    var sum = 0.0
    var i = 0
    while (i < size) {
      sum += txs.get(i).amount
      i += 1
    }
    bh.consume(sum)
  }
}
