package fuse.benchmarks

import java.util.ArrayList
import java.util.List as JList
import org.openjdk.jmh.annotations.*
import scala.compiletime.uninitialized

@State(Scope.Thread)
/** Base class with all the data to execute the benchamrks
  */
class BenchmarkData {
  @Param(Array("10", "100", "1000", "100000"))
  var size: Int = uninitialized

  var transactions: ArrayList[Transaction] = uninitialized
  var transactionsArray: Array[Transaction] = uninitialized

  // Parameter fields for capturing lambda benchmarks
  var targetCategoryElectronics: String = "Electronics"
  var targetStatusCompleted: Int = 1
  var targetCategoryGroceries: String = "Groceries"
  var minAmountGroceries: Double = 50.0
  var cashbackRate: Double = 0.05
  var targetStatusRefunded: Int = 2
  var vipMaxUserId: Int = 200
  var penaltyRate: Double = 0.1
  var targetCategoryFashion: String = "Fashion"
  var minAmountFashion: Double = 20.0
  var maxAmountFashion: Double = 200.0
  var vatRate: Double = 0.22
  var targetStatusPending: Int = 0
  var targetCategoryBooks: String = "Books"
  var maxAmountBooks: Double = 30.0
  var shippingFee: Double = 2.5
  var targetCategoryHome: String = "Home"
  var minAmountHome: Double = 150.0
  var discountRate: Double = 0.9
  var sampleModulo: Int = 5
  var markupRate: Double = 1.05

  @Setup(Level.Trial)
  def setup(): Unit = {
    val txs = new ArrayList[Transaction](size)
    val txArray = new Array[Transaction](size)

    val categories = Array("Electronics", "Groceries", "Fashion", "Books", "Home")

    var i = 0
    while (i < size) {
      val txValue = new Transaction(
        i.toLong,
        i % 1000,
        ((i * 17) % 500) + 0.99,
        categories(i % categories.length),
        i % 4
      )

      txs.add(txValue)
      txArray(i) = txValue
      i += 1
    }

    transactions = txs
    transactionsArray = txArray
  }
}
