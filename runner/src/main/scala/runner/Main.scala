package runner

import fuse.FusedStream.*
import fuse.ParallelCollector
import scala.collection.mutable.ListBuffer

@main def hello(): Unit = {
  given RuntimeConfig = RuntimeConfig.default.copy(workerCount = 3, chunksPerWorker = 2)
  val nums = (1 to 50).toArray

  val customToList = new ParallelCollector[Int, ListBuffer[Int], List[Int]] {
    override def supplier(): ListBuffer[Int] = ListBuffer.empty[Int]
    override def accumulator(buf: ListBuffer[Int], elem: Int): Boolean = {
      buf.addOne(elem)
      false
    }
    override def combine(left: ListBuffer[Int], right: ListBuffer[Int]): ListBuffer[Int] = {
      left.addAll(right)
      left
    }
    override def finisher(buf: ListBuffer[Int]): List[Int] = buf.toList
  }

  val result = FusedStream
    .from(nums)
    .parallel()
    .map(_ * 3)
    .filter(_ % 2 == 0)
    .collect(customToList)

  val expected = nums.map(_ * 3).filter(_ % 2 == 0).toList

}
