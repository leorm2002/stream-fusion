package runner

import fuse.FusedStream.*

@main def hello(): Unit = {
  // val numbers = List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
  val numbers: java.util.List[Int] = new java.util.ArrayList[Int]();
  numbers.addAll(java.util.List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10))

  val result = FusedStream
    .from(numbers)
    .map(i => i * 2)
    .filter(i => i % 2 == 0)
    .collect(toArray)

  println(result)

}
