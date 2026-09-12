package runner

import fuse.{FusedStream, Collector}
import FusedStream.*


@main def hello(): Unit = {
  val i = 10
  // val numbers = List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
  val numbers: Array[Int] = Array(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
  val result = FusedStream
    .from(numbers)
    .filter(_ % 2 == 0)
    .filter(_ > 0)
    .filter(_ > 1)
    .filter(_ > 2)
    .skip(1)
    .map(_ * 10)
    .map(_ * 20)
    .map(_ * 30)
    .flatMap(j => {
      FusedStream.of(j)
      // FusedStream.from((i * 10 to i * 10 + 9).toList)
    })
    .collect(Collector.findFirst[Int])

  println(result)

}
