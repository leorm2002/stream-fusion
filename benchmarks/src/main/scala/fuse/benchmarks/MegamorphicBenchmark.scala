package fuse.benchmarks

import fuse.{Collector, FusedStream}
import fuse.FusedStream.*
import fuse.internal.ArrayListAccessor
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole
import scala.jdk.CollectionConverters.*

/** Benchmark 2: Scenario Megamorfico (Flusso applicativo reale su in-memory store)
  *
  * Simula un flusso di reportistica/analisi aziendale con 8 query eterogenee su un
  * database in-memory rappresentato da un ArrayList di transazioni.
  *
  * Nelle applicazioni reali, diverse query passano lambda differenti agli operatori di stream.
  * In Java Stream e nelle collezioni Scala, i call site interni (Predicate.test, ToDoubleFunction.applyAsDouble)
  * diventano megamorfici (più di 2 target): la inline cache di HotSpot C2 fallisce,
  * disabilitando l'inlining e l'escape analysis, costringendo la JVM a dispatch virtuali
  * e allocazioni ad ogni invocazione.
  *
  * Al contrario, FusedStream effettua fusione e inlining a compile-time (tramite beta-reduction),
  * generando per ciascuna query un loop imperativo monolitico specializzato a zero allocazioni.
  */
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@State(Scope.Thread)
class MegamorphicBenchmark {

  @Benchmark
  @OperationsPerInvocation(8)
  def fusedStream(state: BenchmarkData, bh: Blackhole): Unit = {
    val txs = state.transactions

    // Query 1: Elettronica completata
    val q1 = FusedStream.from(txs)
      .filter(t => t.status == 1 && t.category == "Electronics")
      .map(t => t.amount)
      .collect(summing)
    bh.consume(q1)

    // Query 2: Spesa alimentare sopra 50 euro con cashback (5%)
    val q2 = FusedStream.from(txs)
      .filter(t => t.category == "Groceries" && t.amount > 50.0)
      .map(t => t.amount * 0.05)
      .collect(summing)
    bh.consume(q2)

    // Query 3: Rimborsi per utenti VIP (userId < 200) con penale (10%)
    val q3 = FusedStream.from(txs)
      .filter(t => t.status == 2 && t.userId < 200)
      .map(t => t.amount * 0.1)
      .collect(summing)
    bh.consume(q3)

    // Query 4: Abbigliamento fascia media (20-200) con IVA (22%)
    val q4 = FusedStream.from(txs)
      .filter(t => t.category == "Fashion" && t.amount >= 20.0 && t.amount <= 200.0)
      .map(t => t.amount * 0.22)
      .collect(summing)
    bh.consume(q4)

    // Query 5: Transazioni pendenti per utenti con id pari
    val q5 = FusedStream.from(txs)
      .filter(t => t.status == 0 && (t.userId & 1) == 0)
      .map(t => t.amount)
      .collect(summing)
    bh.consume(q5)

    // Query 6: Libri a basso costo con tariffa di spedizione fissa (2.50)
    val q6 = FusedStream.from(txs)
      .filter(t => t.category == "Books" && t.amount < 30.0)
      .map(t => t.amount + 2.5)
      .collect(summing)
    bh.consume(q6)

    // Query 7: Articoli per la casa completati oltre 150 con sconto (10%)
    val q7 = FusedStream.from(txs)
      .filter(t => t.category == "Home" && t.status == 1 && t.amount > 150.0)
      .map(t => t.amount * 0.9)
      .collect(summing)
    bh.consume(q7)

    // Query 8: Campionamento periodico (userId multiplo di 5) con maggiorazione
    val q8 = FusedStream.from(txs)
      .filter(t => t.userId % 5 == 0)
      .map(t => t.amount * 1.05)
      .collect(summing)
    bh.consume(q8)
  }

  @Benchmark
  @OperationsPerInvocation(8)
  def javaStream(state: BenchmarkData, bh: Blackhole): Unit = {
    val txs = state.transactions

    // Query 1
    val q1 = txs.stream()
      .filter(t => t.status() == 1 && "Electronics".equals(t.category()))
      .mapToDouble(t => t.amount())
      .sum()
    bh.consume(q1)

    // Query 2
    val q2 = txs.stream()
      .filter(t => "Groceries".equals(t.category()) && t.amount() > 50.0)
      .mapToDouble(t => t.amount() * 0.05)
      .sum()
    bh.consume(q2)

    // Query 3
    val q3 = txs.stream()
      .filter(t => t.status() == 2 && t.userId() < 200)
      .mapToDouble(t => t.amount() * 0.1)
      .sum()
    bh.consume(q3)

    // Query 4
    val q4 = txs.stream()
      .filter(t => "Fashion".equals(t.category()) && t.amount() >= 20.0 && t.amount() <= 200.0)
      .mapToDouble(t => t.amount() * 0.22)
      .sum()
    bh.consume(q4)

    // Query 5
    val q5 = txs.stream()
      .filter(t => t.status() == 0 && (t.userId() & 1) == 0)
      .mapToDouble(t => t.amount())
      .sum()
    bh.consume(q5)

    // Query 6
    val q6 = txs.stream()
      .filter(t => "Books".equals(t.category()) && t.amount() < 30.0)
      .mapToDouble(t => t.amount() + 2.5)
      .sum()
    bh.consume(q6)

    // Query 7
    val q7 = txs.stream()
      .filter(t => "Home".equals(t.category()) && t.status() == 1 && t.amount() > 150.0)
      .mapToDouble(t => t.amount() * 0.9)
      .sum()
    bh.consume(q7)

    // Query 8
    val q8 = txs.stream()
      .filter(t => t.userId() % 5 == 0)
      .mapToDouble(t => t.amount() * 1.05)
      .sum()
    bh.consume(q8)
  }

  @Benchmark
  @OperationsPerInvocation(8)
  def scalaStream(state: BenchmarkData, bh: Blackhole): Unit = {
    val txs = state.transactions

    // Query 1
    val q1 = txs.asScala.view
      .filter(t => t.status == 1 && t.category == "Electronics")
      .map(t => t.amount)
      .sum
    bh.consume(q1)

    // Query 2
    val q2 = txs.asScala.view
      .filter(t => t.category == "Groceries" && t.amount > 50.0)
      .map(t => t.amount * 0.05)
      .sum
    bh.consume(q2)

    // Query 3
    val q3 = txs.asScala.view
      .filter(t => t.status == 2 && t.userId < 200)
      .map(t => t.amount * 0.1)
      .sum
    bh.consume(q3)

    // Query 4
    val q4 = txs.asScala.view
      .filter(t => t.category == "Fashion" && t.amount >= 20.0 && t.amount <= 200.0)
      .map(t => t.amount * 0.22)
      .sum
    bh.consume(q4)

    // Query 5
    val q5 = txs.asScala.view
      .filter(t => t.status == 0 && (t.userId & 1) == 0)
      .map(t => t.amount)
      .sum
    bh.consume(q5)

    // Query 6
    val q6 = txs.asScala.view
      .filter(t => t.category == "Books" && t.amount < 30.0)
      .map(t => t.amount + 2.5)
      .sum
    bh.consume(q6)

    // Query 7
    val q7 = txs.asScala.view
      .filter(t => t.category == "Home" && t.status == 1 && t.amount > 150.0)
      .map(t => t.amount * 0.9)
      .sum
    bh.consume(q7)

    // Query 8
    val q8 = txs.asScala.view
      .filter(t => t.userId % 5 == 0)
      .map(t => t.amount * 1.05)
      .sum
    bh.consume(q8)
  }

  @Benchmark
  @OperationsPerInvocation(8)
  def javaManual(state: BenchmarkData, bh: Blackhole): Unit = {
    val txs = state.transactions
    val size = txs.size()

    // Query 1
    var s1 = 0.0
    var i = 0
    while (i < size) {
      val t = txs.get(i)
      if (t.status == 1 && t.category == "Electronics") {
        s1 += t.amount
      }
      i += 1
    }
    bh.consume(s1)

    // Query 2
    var s2 = 0.0
    i = 0
    while (i < size) {
      val t = txs.get(i)
      if (t.category == "Groceries" && t.amount > 50.0) {
        s2 += t.amount * 0.05
      }
      i += 1
    }
    bh.consume(s2)

    // Query 3
    var s3 = 0.0
    i = 0
    while (i < size) {
      val t = txs.get(i)
      if (t.status == 2 && t.userId < 200) {
        s3 += t.amount * 0.1
      }
      i += 1
    }
    bh.consume(s3)

    // Query 4
    var s4 = 0.0
    i = 0
    while (i < size) {
      val t = txs.get(i)
      if (t.category == "Fashion" && t.amount >= 20.0 && t.amount <= 200.0) {
        s4 += t.amount * 0.22
      }
      i += 1
    }
    bh.consume(s4)

    // Query 5
    var s5 = 0.0
    i = 0
    while (i < size) {
      val t = txs.get(i)
      if (t.status == 0 && (t.userId & 1) == 0) {
        s5 += t.amount
      }
      i += 1
    }
    bh.consume(s5)

    // Query 6
    var s6 = 0.0
    i = 0
    while (i < size) {
      val t = txs.get(i)
      if (t.category == "Books" && t.amount < 30.0) {
        s6 += t.amount + 2.5
      }
      i += 1
    }
    bh.consume(s6)

    // Query 7
    var s7 = 0.0
    i = 0
    while (i < size) {
      val t = txs.get(i)
      if (t.category == "Home" && t.status == 1 && t.amount > 150.0) {
        s7 += t.amount * 0.9
      }
      i += 1
    }
    bh.consume(s7)

    // Query 8
    var s8 = 0.0
    i = 0
    while (i < size) {
      val t = txs.get(i)
      if (t.userId % 5 == 0) {
        s8 += t.amount * 1.05
      }
      i += 1
    }
    bh.consume(s8)
  }
}
