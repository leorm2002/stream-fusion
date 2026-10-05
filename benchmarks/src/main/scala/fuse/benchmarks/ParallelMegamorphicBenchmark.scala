package fuse.benchmarks

import fuse.{Collector, FusedStream}
import fuse.FusedStream.*
import java.util.concurrent.{Callable, ForkJoinPool, TimeUnit}
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole

/** Benchmark 3: Scenario Megamorfico Parallelo (8 query eterogenee)
  *
  * Esecuzione parallela multi-thread dello scenario megamorfico applicativo su larga scala. Esegue le stesse identiche 8 query dello scenario sequenziale per consentire un
  * confronto diretto e simmetrico.
  *
  * Confronta:
  *   1. FusedStream (.parallel())
  *   2. Java Parallel Stream (.parallelStream())
  *   3. Java Manuale Concorrente (chunking manuale indicizzato classico su ForkJoinPool)
  */
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(3)
@State(Scope.Thread)
class ParallelMegamorphicBenchmark {

  private val pool: ForkJoinPool = ForkJoinPool.commonPool()
  private val workers: Int = Runtime.getRuntime().availableProcessors()

  @Benchmark
  @OperationsPerInvocation(8)
  def fusedStreamParallel(state: BenchmarkData, bh: Blackhole): Unit = {
    val txs = state.transactions

    // Query 1: Elettronica completata
    val q1 = FusedStream
      .from(txs)
      .parallel()
      .filter(t => t.status == 1 && t.category == "Electronics")
      .map(t => t.amount)
      .collect(summing)
    bh.consume(q1)

    // Query 2: Spesa alimentare sopra 50 euro con cashback (5%)
    val q2 = FusedStream
      .from(txs)
      .parallel()
      .filter(t => t.category == "Groceries" && t.amount > 50.0)
      .map(t => t.amount * 0.05)
      .collect(summing)
    bh.consume(q2)

    // Query 3: Rimborsi per utenti VIP (userId < 200) con penale (10%)
    val q3 = FusedStream
      .from(txs)
      .parallel()
      .filter(t => t.status == 2 && t.userId < 200)
      .map(t => t.amount * 0.1)
      .collect(summing)
    bh.consume(q3)

    // Query 4: Abbigliamento fascia media (20-200) con IVA (22%)
    val q4 = FusedStream
      .from(txs)
      .parallel()
      .filter(t => t.category == "Fashion" && t.amount >= 20.0 && t.amount <= 200.0)
      .map(t => t.amount * 0.22)
      .collect(summing)
    bh.consume(q4)

    // Query 5: Transazioni pendenti per utenti con id pari
    val q5 = FusedStream
      .from(txs)
      .parallel()
      .filter(t => t.status == 0 && (t.userId & 1) == 0)
      .map(t => t.amount)
      .collect(summing)
    bh.consume(q5)

    // Query 6: Libri a basso costo con tariffa di spedizione fissa (2.50)
    val q6 = FusedStream
      .from(txs)
      .parallel()
      .filter(t => t.category == "Books" && t.amount < 30.0)
      .map(t => t.amount + 2.5)
      .collect(summing)
    bh.consume(q6)

    // Query 7: Articoli per la casa completati oltre 150 con sconto (10%)
    val q7 = FusedStream
      .from(txs)
      .parallel()
      .filter(t => t.category == "Home" && t.status == 1 && t.amount > 150.0)
      .map(t => t.amount * 0.9)
      .collect(summing)
    bh.consume(q7)

    // Query 8: Campionamento periodico (userId multiplo di 5) con maggiorazione
    val q8 = FusedStream
      .from(txs)
      .parallel()
      .filter(t => t.userId % 5 == 0)
      .map(t => t.amount * 1.05)
      .collect(summing)
    bh.consume(q8)
  }

  @Benchmark
  @OperationsPerInvocation(8)
  def javaParallelStream(state: BenchmarkData, bh: Blackhole): Unit = {
    val txs = state.transactions

    // Query 1
    val q1 = txs
      .parallelStream()
      .filter(t => t.status() == 1 && "Electronics".equals(t.category()))
      .mapToDouble(t => t.amount())
      .sum()
    bh.consume(q1)

    // Query 2
    val q2 = txs
      .parallelStream()
      .filter(t => "Groceries".equals(t.category()) && t.amount() > 50.0)
      .mapToDouble(t => t.amount() * 0.05)
      .sum()
    bh.consume(q2)

    // Query 3
    val q3 = txs
      .parallelStream()
      .filter(t => t.status() == 2 && t.userId() < 200)
      .mapToDouble(t => t.amount() * 0.1)
      .sum()
    bh.consume(q3)

    // Query 4
    val q4 = txs
      .parallelStream()
      .filter(t => "Fashion".equals(t.category()) && t.amount() >= 20.0 && t.amount <= 200.0)
      .mapToDouble(t => t.amount() * 0.22)
      .sum()
    bh.consume(q4)

    // Query 5
    val q5 = txs
      .parallelStream()
      .filter(t => t.status() == 0 && (t.userId() & 1) == 0)
      .mapToDouble(t => t.amount())
      .sum()
    bh.consume(q5)

    // Query 6
    val q6 = txs
      .parallelStream()
      .filter(t => "Books".equals(t.category()) && t.amount() < 30.0)
      .mapToDouble(t => t.amount() + 2.5)
      .sum()
    bh.consume(q6)

    // Query 7
    val q7 = txs
      .parallelStream()
      .filter(t => "Home".equals(t.category()) && t.status() == 1 && t.amount() > 150.0)
      .mapToDouble(t => t.amount() * 0.9)
      .sum()
    bh.consume(q7)

    // Query 8
    val q8 = txs
      .parallelStream()
      .filter(t => t.userId() % 5 == 0)
      .mapToDouble(t => t.amount() * 1.05)
      .sum()
    bh.consume(q8)
  }

  @Benchmark
  @OperationsPerInvocation(8)
  def javaManualParallel(state: BenchmarkData, bh: Blackhole): Unit = {
    val txs = state.transactions
    val size = txs.size()

    def runChunked(f: (java.util.ArrayList[Transaction], Int, Int) => Double): Double = {
      val tasks = new java.util.ArrayList[Callable[Double]](workers)
      var w = 0
      while (w < workers) {
        val from = (w.toLong * size / workers).toInt
        val until = ((w + 1).toLong * size / workers).toInt
        tasks.add(new Callable[Double] {
          override def call(): Double = f(txs, from, until)
        })
        w += 1
      }
      val futures = pool.invokeAll(tasks)
      var total = 0.0
      var idx = 0
      while (idx < futures.size()) {
        total += futures.get(idx).get()
        idx += 1
      }
      total
    }

    // Query 1
    val q1 = runChunked { (list, from, until) =>
      var sum = 0.0
      var i = from
      while (i < until) {
        val t = list.get(i)
        if (t.status == 1 && t.category == "Electronics") sum += t.amount
        i += 1
      }
      sum
    }
    bh.consume(q1)

    // Query 2
    val q2 = runChunked { (list, from, until) =>
      var sum = 0.0
      var i = from
      while (i < until) {
        val t = list.get(i)
        if (t.category == "Groceries" && t.amount > 50.0) sum += t.amount * 0.05
        i += 1
      }
      sum
    }
    bh.consume(q2)

    // Query 3
    val q3 = runChunked { (list, from, until) =>
      var sum = 0.0
      var i = from
      while (i < until) {
        val t = list.get(i)
        if (t.status == 2 && t.userId < 200) sum += t.amount * 0.1
        i += 1
      }
      sum
    }
    bh.consume(q3)

    // Query 4
    val q4 = runChunked { (list, from, until) =>
      var sum = 0.0
      var i = from
      while (i < until) {
        val t = list.get(i)
        if (t.category == "Fashion" && t.amount >= 20.0 && t.amount <= 200.0) sum += t.amount * 0.22
        i += 1
      }
      sum
    }
    bh.consume(q4)

    // Query 5
    val q5 = runChunked { (list, from, until) =>
      var sum = 0.0
      var i = from
      while (i < until) {
        val t = list.get(i)
        if (t.status == 0 && (t.userId & 1) == 0) sum += t.amount
        i += 1
      }
      sum
    }
    bh.consume(q5)

    // Query 6
    val q6 = runChunked { (list, from, until) =>
      var sum = 0.0
      var i = from
      while (i < until) {
        val t = list.get(i)
        if (t.category == "Books" && t.amount < 30.0) sum += t.amount + 2.5
        i += 1
      }
      sum
    }
    bh.consume(q6)

    // Query 7
    val q7 = runChunked { (list, from, until) =>
      var sum = 0.0
      var i = from
      while (i < until) {
        val t = list.get(i)
        if (t.category == "Home" && t.status == 1 && t.amount > 150.0) sum += t.amount * 0.9
        i += 1
      }
      sum
    }
    bh.consume(q7)

    // Query 8
    val q8 = runChunked { (list, from, until) =>
      var sum = 0.0
      var i = from
      while (i < until) {
        val t = list.get(i)
        if (t.userId % 5 == 0) sum += t.amount * 1.05
        i += 1
      }
      sum
    }
    bh.consume(q8)
  }
}
