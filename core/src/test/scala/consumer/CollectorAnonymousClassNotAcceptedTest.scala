package consumer

import munit.FunSuite
import scala.compiletime.testing.typeCheckErrors

class CollectorAnonymousClassNotAcceptedTest extends FunSuite {

  test("direct anonymous inline collectors are rejected") {
    val errors = typeCheckErrors("""
      import scala.collection.mutable.ListBuffer
      import fuse.*
      import fuse.FusedStream.*
      FusedStream.from(Array(1, 2)).collect(new Collector[Int, ListBuffer[Int], List[Int], Exhaustive] {
        inline def supplier(): ListBuffer[Int] = ListBuffer.empty[Int]
        inline def accumulator(buf: ListBuffer[Int], E: Int): Boolean = { buf.addOne(E); false }
        inline def finisher(buf: ListBuffer[Int]): List[Int] = buf.toList
      })
    """)

    val expected = "StreamFusion: collect requires a named concrete collector class. Anonymous collectors and references typed as Collector are not supported"
    assert(errors.exists(_.message.contains(expected)), errors.map(_.message).mkString("\n"))
  }

}
