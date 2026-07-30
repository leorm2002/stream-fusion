package fuse

class InfiniteRepeater(base: String) extends Iterable[String] {
  override def iterator: Iterator[String] = new Iterator[String] {
    private var current: String = base

    override def hasNext: Boolean = true

    override def next(): String = {
      val result = current
      current = current + base
      result
    }
  }
}

object InfiniteRepeater {
  def apply(base: Any): InfiniteRepeater = new InfiniteRepeater(base.toString)
}
