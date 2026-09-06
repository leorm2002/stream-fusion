package fuse.benchmarks

import fuse.{Collector, FusedStream}
import fuse.FusedStream.*
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole
import java.util.function.ToIntFunction

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 8, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
@State(Scope.Thread)
class MegamorphicBenchmark {
  private val monoMapper: ToIntFunction[String] = s => s.length

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  private def javaPipeline(xs: java.util.List[String], mapper: ToIntFunction[String]): Array[Int] =
    xs.stream().mapToInt(mapper).toArray

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  private def fused1(xs: java.util.List[String]): Array[Int] =
    FusedStream.from(xs).map(_.length).collect(Collector.toArray)

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  private def fused2(xs: java.util.List[String]): Array[Int] =
    FusedStream.from(xs).map(_.length).collect(Collector.toArray)

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  private def fused3(xs: java.util.List[String]): Array[Int] =
    FusedStream.from(xs).map(_.length).collect(Collector.toArray)

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  private def fused4(xs: java.util.List[String]): Array[Int] =
    FusedStream.from(xs).map(_.length).collect(Collector.toArray)

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  private def fused5(xs: java.util.List[String]): Array[Int] =
    FusedStream.from(xs).map(_.length).collect(Collector.toArray)

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  private def fused6(xs: java.util.List[String]): Array[Int] =
    FusedStream.from(xs).map(_.length).collect(Collector.toArray)

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  private def fused7(xs: java.util.List[String]): Array[Int] =
    FusedStream.from(xs).map(_.length).collect(Collector.toArray)

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  private def fused8(xs: java.util.List[String]): Array[Int] =
    FusedStream.from(xs).map(_.length).collect(Collector.toArray)

  @Benchmark
  @OperationsPerInvocation(8)
  def javaMegamorphic(state: BenchmarkData, bh: Blackhole): Unit = {
    val xs = state.javaStrings

    bh.consume(javaPipeline(xs, s => s.length))
    bh.consume(javaPipeline(xs, s => s.length))
    bh.consume(javaPipeline(xs, s => s.length))
    bh.consume(javaPipeline(xs, s => s.length))
    bh.consume(javaPipeline(xs, s => s.length))
    bh.consume(javaPipeline(xs, s => s.length))
    bh.consume(javaPipeline(xs, s => s.length))
    bh.consume(javaPipeline(xs, s => s.length))
  }

@Benchmark
@OperationsPerInvocation(8)
def javaMonomorphic(state: BenchmarkData, bh: Blackhole): Unit = {
  val xs = state.javaStrings

  bh.consume(javaPipeline(xs, monoMapper))
  bh.consume(javaPipeline(xs, monoMapper))
  bh.consume(javaPipeline(xs, monoMapper))
  bh.consume(javaPipeline(xs, monoMapper))
  bh.consume(javaPipeline(xs, monoMapper))
  bh.consume(javaPipeline(xs, monoMapper))
  bh.consume(javaPipeline(xs, monoMapper))
  bh.consume(javaPipeline(xs, monoMapper))
}
  @Benchmark
  @OperationsPerInvocation(8)
  def fusedMegamorphic(state: BenchmarkData, bh: Blackhole): Unit = {
    val xs = state.javaStrings

    bh.consume(fused1(xs))
    bh.consume(fused2(xs))
    bh.consume(fused3(xs))
    bh.consume(fused4(xs))
    bh.consume(fused5(xs))
    bh.consume(fused6(xs))
    bh.consume(fused7(xs))
    bh.consume(fused8(xs))
  }

@Benchmark
def javaManualIndexed(state: BenchmarkData, bh: Blackhole): Unit = {
  val xs = state.javaStrings
  val size = xs.size()
  val out = new Array[Int](size)

  var i = 0
  while (i < size) {
    out(i) = xs.get(i).length
    i += 1
  }

  bh.consume(out)
}
}
