package fuse.benchmarks

import java.util.ArrayList
import java.util.List as JList
import org.openjdk.jmh.annotations.*
import scala.compiletime.uninitialized

@State(Scope.Thread)
class BenchmarkData {
  @Param(Array("1000", "100000"))
  var size: Int = uninitialized

  var scalaStrings: List[String] = uninitialized
  var scalaPeople: List[Person] = uninitialized
  var scalaPoints: List[Point] = uninitialized

  var javaStrings: JList[String] = uninitialized
  var javaPeople: JList[Person] = uninitialized
  var javaPoints: JList[Point] = uninitialized

  @Setup(Level.Trial)
  def setup(): Unit = {
    val strings = List.newBuilder[String]
    val people = List.newBuilder[Person]
    val points = List.newBuilder[Point]

    val jStrings = new ArrayList[String](size)
    val jPeople = new ArrayList[Person](size)
    val jPoints = new ArrayList[Point](size)

    var i = 0
    while (i < size) {
      val stringValue = if (i % 4 == 0) "" else s"value-${i % 1024}"
      val personValue = new Person(s"person-${i % 1024}", 10 + (i % 20), false)
      val pointValue = new Point(
        if (i % 4 == 0) -i - 1 else i,
        if (i % 7 == 0) -i - 1 else i
      )

      strings += stringValue
      people += personValue
      points += pointValue

      jStrings.add(stringValue)
      jPeople.add(personValue)
      jPoints.add(pointValue)
      i += 1
    }

    scalaStrings = strings.result()
    scalaPeople = people.result()
    scalaPoints = points.result()
    javaStrings = jStrings
    javaPeople = jPeople
    javaPoints = jPoints
  }
}
