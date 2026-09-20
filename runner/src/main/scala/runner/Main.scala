package runner

import fuse.FusedStream.*

@main def hello(): Unit = {
  val i = 10
  // val numbers = List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
  val numbers: Array[Int] = Array(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
  val result = FusedStream
    .from(numbers)
    .filter(i => i % 2 == 0)
    .skip(1)
    .map(i => i * 10)
    .collect(summing)

  println(result)

}
