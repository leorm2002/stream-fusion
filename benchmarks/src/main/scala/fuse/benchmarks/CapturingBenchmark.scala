package fuse.benchmarks

import fuse.{Collector, FusedStream}
import fuse.FusedStream.*
import fuse.internal.ArrayListAccessor
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole
import scala.jdk.CollectionConverters.*

/** Benchmark 4: Scenario Megamorfico con Capturing Lambdas
  *
  * Simula lo stesso scenario analitico aziendale su 8 query eterogenee, ma in cui le funzioni lambda catturano variabili dall'ambiente locale circostante (parametri di soglia,
  * aliquote, categorie, identificatori).
  *
  * Differenza fondamentale con le lambda non-capturing:
  *   - Le lambda non-capturing possono essere istanziate una sola volta e salvate in costanti statiche dalla JVM (tramite LambdaMetafactory).
  *   - Le lambda catturanti (capturing) richiedono l'allocazione di una nuova istanza di closure sullo heap ad ogni invocazione della query, a meno che il JIT non riesca a
  *     dimostrare l'assenza di escape tramite Escape Analysis.
  *
  * A Tier 0 (Interprete) e Tier 1 (C1), l'Escape Analysis e' assente: sia Java Stream che Scala View generano continua allocazione di oggetti closure e dispatch virtuali, con
  * conseguente crollo prestazionale e pressione su GC.
  *
  * Al contrario, FusedStream inietta i corpi delle lambda e le variabili catturate direttamente nel corpo del ciclo while a tempo di compilazione tramite macro (beta-reduction).
  * Le variabili catturate diventano accessi a variabili locali sullo stack JVM: zero allocazioni di closure, zero dispatch virtuali, zero overhead a qualsiasi livello JIT.
  */
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@State(Scope.Thread)
class CapturingBenchmark {

  @Benchmark
  @OperationsPerInvocation(8)
  def fusedStream(state: BenchmarkData, bh: Blackhole): Unit = {
    val txs = state.transactions
    val catElectronics = state.targetCategoryElectronics
    val statusCompleted = state.targetStatusCompleted
    val catGroceries = state.targetCategoryGroceries
    val minGroceries = state.minAmountGroceries
    val cashback = state.cashbackRate
    val statusRefunded = state.targetStatusRefunded
    val vipUserId = state.vipMaxUserId
    val penalty = state.penaltyRate
    val catFashion = state.targetCategoryFashion
    val minFashion = state.minAmountFashion
    val maxFashion = state.maxAmountFashion
    val vat = state.vatRate
    val statusPending = state.targetStatusPending
    val catBooks = state.targetCategoryBooks
    val maxBooks = state.maxAmountBooks
    val shipping = state.shippingFee
    val catHome = state.targetCategoryHome
    val minHome = state.minAmountHome
    val discount = state.discountRate
    val modulo = state.sampleModulo
    val markup = state.markupRate

    // Query 1: Elettronica completata
    val q1 = FusedStream
      .from(txs)
      .filter(t => t.status == statusCompleted && t.category == catElectronics)
      .map(t => t.amount)
      .collect(summing)
    bh.consume(q1)

    // Query 2: Spesa alimentare sopra soglia con cashback
    val q2 = FusedStream
      .from(txs)
      .filter(t => t.category == catGroceries && t.amount > minGroceries)
      .map(t => t.amount * cashback)
      .collect(summing)
    bh.consume(q2)

    // Query 3: Rimborsi per utenti VIP con penale
    val q3 = FusedStream
      .from(txs)
      .filter(t => t.status == statusRefunded && t.userId < vipUserId)
      .map(t => t.amount * penalty)
      .collect(summing)
    bh.consume(q3)

    // Query 4: Abbigliamento fascia media con IVA
    val q4 = FusedStream
      .from(txs)
      .filter(t => t.category == catFashion && t.amount >= minFashion && t.amount <= maxFashion)
      .map(t => t.amount * vat)
      .collect(summing)
    bh.consume(q4)

    // Query 5: Transazioni pendenti per utenti con id pari
    val q5 = FusedStream
      .from(txs)
      .filter(t => t.status == statusPending && (t.userId & 1) == 0)
      .map(t => t.amount)
      .collect(summing)
    bh.consume(q5)

    // Query 6: Libri a basso costo con tariffa di spedizione fissa
    val q6 = FusedStream
      .from(txs)
      .filter(t => t.category == catBooks && t.amount < maxBooks)
      .map(t => t.amount + shipping)
      .collect(summing)
    bh.consume(q6)

    // Query 7: Articoli per la casa completati oltre soglia con sconto
    val q7 = FusedStream
      .from(txs)
      .filter(t => t.category == catHome && t.status == statusCompleted && t.amount > minHome)
      .map(t => t.amount * discount)
      .collect(summing)
    bh.consume(q7)

    // Query 8: Campionamento periodico con maggiorazione
    val q8 = FusedStream
      .from(txs)
      .filter(t => t.userId % modulo == 0)
      .map(t => t.amount * markup)
      .collect(summing)
    bh.consume(q8)
  }

  @Benchmark
  @OperationsPerInvocation(8)
  def javaStream(state: BenchmarkData, bh: Blackhole): Unit = {
    val txs = state.transactions
    val catElectronics = state.targetCategoryElectronics
    val statusCompleted = state.targetStatusCompleted
    val catGroceries = state.targetCategoryGroceries
    val minGroceries = state.minAmountGroceries
    val cashback = state.cashbackRate
    val statusRefunded = state.targetStatusRefunded
    val vipUserId = state.vipMaxUserId
    val penalty = state.penaltyRate
    val catFashion = state.targetCategoryFashion
    val minFashion = state.minAmountFashion
    val maxFashion = state.maxAmountFashion
    val vat = state.vatRate
    val statusPending = state.targetStatusPending
    val catBooks = state.targetCategoryBooks
    val maxBooks = state.maxAmountBooks
    val shipping = state.shippingFee
    val catHome = state.targetCategoryHome
    val minHome = state.minAmountHome
    val discount = state.discountRate
    val modulo = state.sampleModulo
    val markup = state.markupRate

    // Query 1
    val q1 = txs
      .stream()
      .filter(t => t.status() == statusCompleted && catElectronics.equals(t.category()))
      .mapToDouble(t => t.amount())
      .sum()
    bh.consume(q1)

    // Query 2
    val q2 = txs
      .stream()
      .filter(t => catGroceries.equals(t.category()) && t.amount() > minGroceries)
      .mapToDouble(t => t.amount() * cashback)
      .sum()
    bh.consume(q2)

    // Query 3
    val q3 = txs
      .stream()
      .filter(t => t.status() == statusRefunded && t.userId() < vipUserId)
      .mapToDouble(t => t.amount() * penalty)
      .sum()
    bh.consume(q3)

    // Query 4
    val q4 = txs
      .stream()
      .filter(t => catFashion.equals(t.category()) && t.amount() >= minFashion && t.amount() <= maxFashion)
      .mapToDouble(t => t.amount() * vat)
      .sum()
    bh.consume(q4)

    // Query 5
    val q5 = txs
      .stream()
      .filter(t => t.status() == statusPending && (t.userId() & 1) == 0)
      .mapToDouble(t => t.amount())
      .sum()
    bh.consume(q5)

    // Query 6
    val q6 = txs
      .stream()
      .filter(t => catBooks.equals(t.category()) && t.amount() < maxBooks)
      .mapToDouble(t => t.amount() + shipping)
      .sum()
    bh.consume(q6)

    // Query 7
    val q7 = txs
      .stream()
      .filter(t => catHome.equals(t.category()) && t.status() == statusCompleted && t.amount() > minHome)
      .mapToDouble(t => t.amount() * discount)
      .sum()
    bh.consume(q7)

    // Query 8
    val q8 = txs
      .stream()
      .filter(t => t.userId() % modulo == 0)
      .mapToDouble(t => t.amount() * markup)
      .sum()
    bh.consume(q8)
  }

  @Benchmark
  @OperationsPerInvocation(8)
  def scalaStream(state: BenchmarkData, bh: Blackhole): Unit = {
    val txs = state.transactions
    val catElectronics = state.targetCategoryElectronics
    val statusCompleted = state.targetStatusCompleted
    val catGroceries = state.targetCategoryGroceries
    val minGroceries = state.minAmountGroceries
    val cashback = state.cashbackRate
    val statusRefunded = state.targetStatusRefunded
    val vipUserId = state.vipMaxUserId
    val penalty = state.penaltyRate
    val catFashion = state.targetCategoryFashion
    val minFashion = state.minAmountFashion
    val maxFashion = state.maxAmountFashion
    val vat = state.vatRate
    val statusPending = state.targetStatusPending
    val catBooks = state.targetCategoryBooks
    val maxBooks = state.maxAmountBooks
    val shipping = state.shippingFee
    val catHome = state.targetCategoryHome
    val minHome = state.minAmountHome
    val discount = state.discountRate
    val modulo = state.sampleModulo
    val markup = state.markupRate

    // Query 1
    val q1 = txs.asScala.view
      .filter(t => t.status == statusCompleted && t.category == catElectronics)
      .map(t => t.amount)
      .sum
    bh.consume(q1)

    // Query 2
    val q2 = txs.asScala.view
      .filter(t => t.category == catGroceries && t.amount > minGroceries)
      .map(t => t.amount * cashback)
      .sum
    bh.consume(q2)

    // Query 3
    val q3 = txs.asScala.view
      .filter(t => t.status == statusRefunded && t.userId < vipUserId)
      .map(t => t.amount * penalty)
      .sum
    bh.consume(q3)

    // Query 4
    val q4 = txs.asScala.view
      .filter(t => t.category == catFashion && t.amount >= minFashion && t.amount <= maxFashion)
      .map(t => t.amount * vat)
      .sum
    bh.consume(q4)

    // Query 5
    val q5 = txs.asScala.view
      .filter(t => t.status == statusPending && (t.userId & 1) == 0)
      .map(t => t.amount)
      .sum
    bh.consume(q5)

    // Query 6
    val q6 = txs.asScala.view
      .filter(t => t.category == catBooks && t.amount < maxBooks)
      .map(t => t.amount + shipping)
      .sum
    bh.consume(q6)

    // Query 7
    val q7 = txs.asScala.view
      .filter(t => t.category == catHome && t.status == statusCompleted && t.amount > minHome)
      .map(t => t.amount * discount)
      .sum
    bh.consume(q7)

    // Query 8
    val q8 = txs.asScala.view
      .filter(t => t.userId % modulo == 0)
      .map(t => t.amount * markup)
      .sum
    bh.consume(q8)
  }

  @Benchmark
  @OperationsPerInvocation(8)
  def javaManual(state: BenchmarkData, bh: Blackhole): Unit = {
    val txs = state.transactions
    val size = txs.size()
    val catElectronics = state.targetCategoryElectronics
    val statusCompleted = state.targetStatusCompleted
    val catGroceries = state.targetCategoryGroceries
    val minGroceries = state.minAmountGroceries
    val cashback = state.cashbackRate
    val statusRefunded = state.targetStatusRefunded
    val vipUserId = state.vipMaxUserId
    val penalty = state.penaltyRate
    val catFashion = state.targetCategoryFashion
    val minFashion = state.minAmountFashion
    val maxFashion = state.maxAmountFashion
    val vat = state.vatRate
    val statusPending = state.targetStatusPending
    val catBooks = state.targetCategoryBooks
    val maxBooks = state.maxAmountBooks
    val shipping = state.shippingFee
    val catHome = state.targetCategoryHome
    val minHome = state.minAmountHome
    val discount = state.discountRate
    val modulo = state.sampleModulo
    val markup = state.markupRate

    // Query 1
    var s1 = 0.0
    var i = 0
    while (i < size) {
      val t = txs.get(i)
      if (t.status == statusCompleted && catElectronics.equals(t.category)) {
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
      if (catGroceries.equals(t.category) && t.amount > minGroceries) {
        s2 += t.amount * cashback
      }
      i += 1
    }
    bh.consume(s2)

    // Query 3
    var s3 = 0.0
    i = 0
    while (i < size) {
      val t = txs.get(i)
      if (t.status == statusRefunded && t.userId < vipUserId) {
        s3 += t.amount * penalty
      }
      i += 1
    }
    bh.consume(s3)

    // Query 4
    var s4 = 0.0
    i = 0
    while (i < size) {
      val t = txs.get(i)
      if (catFashion.equals(t.category) && t.amount >= minFashion && t.amount <= maxFashion) {
        s4 += t.amount * vat
      }
      i += 1
    }
    bh.consume(s4)

    // Query 5
    var s5 = 0.0
    i = 0
    while (i < size) {
      val t = txs.get(i)
      if (t.status == statusPending && (t.userId & 1) == 0) {
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
      if (catBooks.equals(t.category) && t.amount < maxBooks) {
        s6 += t.amount + shipping
      }
      i += 1
    }
    bh.consume(s6)

    // Query 7
    var s7 = 0.0
    i = 0
    while (i < size) {
      val t = txs.get(i)
      if (catHome.equals(t.category) && t.status == statusCompleted && t.amount > minHome) {
        s7 += t.amount * discount
      }
      i += 1
    }
    bh.consume(s7)

    // Query 8
    var s8 = 0.0
    i = 0
    while (i < size) {
      val t = txs.get(i)
      if (t.userId % modulo == 0) {
        s8 += t.amount * markup
      }
      i += 1
    }
    bh.consume(s8)
  }
}
