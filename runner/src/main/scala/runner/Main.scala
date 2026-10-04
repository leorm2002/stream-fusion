package runner

import fuse.FusedStream.*
import scala.collection.mutable.ListBuffer
import scala.collection.mutable

class MyToList[T] extends ParallelCollector[T, ListBuffer[T], List[T]] {

  inline def combine(left: mutable.ListBuffer[T], right: mutable.ListBuffer[T]): mutable.ListBuffer[T] = {
    left.addAll(right)
    left
  }

  inline def supplier(): ListBuffer[T] = ListBuffer.empty[T]
  inline def accumulator(buf: ListBuffer[T], E: T): Boolean = { buf.addOne(E); false }
  inline def finisher(buf: ListBuffer[T]): List[T] = buf.toList
}

@main def hello(): Unit = {
  given RuntimeConfig = RuntimeConfig.default.copy(workerCount = 3, chunksPerWorker = 2)
  val nums = (1 to 50).toArray

  val result = FusedStream
    .from(nums)
    .parallel()
    .map(_ * 3)
    .filter(_ % 2 == 0)
    .collect(new MyToList)

  val expected = nums.map(_ * 3).filter(_ % 2 == 0).toList

  val x = 10;

}
