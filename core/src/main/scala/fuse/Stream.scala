package fuse


// Represents the possible operations on the stream

sealed trait Stream[A] {

  /** Filter the elements in the stream
    */
  def filter(pred: A => Boolean): Stream[A]

  /** Performs a mapping of the element with the given function
    */
  def map[B](f: A => B): Stream[B]

  /** By defining a FusedStream inside the flatMap function, you can perform a 1:N mapping (one-to-many).
    *
    * Note that the inner [[FusedStream]] must be defined inline directly within the `flatMap` body. This allows the stream transformer to fuse the inner operation into the outer
    * execution pipeline without incurring extra allocation overhead.
    *
    * ===Example===
    * {{{
    * val result = FusedStream
    *   .from(nums)
    *   .flatMap(a => FusedStream.from(InfiniteRepeater(a)).map(x => (a, x)).limit(4))
    *   .limit(20)
    *   .collect(Collector.toList)
    * }}}
    */
  def flatMap[B](f: A => SequentialStream[B]): Stream[B]
}

sealed trait SequentialStream[A] extends Stream[A] {
  override def filter(pred: A => Boolean): SequentialStream[A]

  override def map[B](f: A => B): SequentialStream[B]

  override def flatMap[B](f: A => SequentialStream[B]): SequentialStream[B]

  def skip(n: Int): SequentialStream[A]

  def limit(n: Int): SequentialStream[A]

}

/** Represents a stream that may be:
  *   - executed a a sequential stream
  *   - made paralle (the only way is by calling paralle() a the first operator)
  */
sealed trait ParallelizableSource[A] extends SequentialStream[A] {

  /** If added to the pipeline the library will try to make the stream parallel, if it's not possible a compilation error will be emitted
    */
  def parallel(): ParallelStream[A]

}

sealed trait ParallelStream[A] extends Stream[A] {

  override def filter(pred: A => Boolean): ParallelStream[A]

  override def map[B](f: A => B): ParallelStream[B]

  override def flatMap[B](f: A => SequentialStream[B]): ParallelStream[B]

}
