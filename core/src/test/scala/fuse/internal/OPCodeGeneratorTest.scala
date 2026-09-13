package fuse.internal

import fuse.RuntimeConfig
import java.util.concurrent.{CountDownLatch, ForkJoinPool, TimeUnit}
import java.util.concurrent.atomic.{AtomicInteger, AtomicIntegerArray}
import munit.FunSuite
import scala.concurrent.ExecutionContext
import fuse.internal.OPCodeGenerator

class OPCodeGeneratorTest extends FunSuite {

  test("chunk counts handle empty, tiny and maximum sized sources without overflow") {
    val config = RuntimeConfig(ExecutionContext.parasitic, 8)
    assertEquals(config.chunksPerWorker, 8)
    assertEquals(OPCodeGenerator.computeCount(0, config), 0)
    assertEquals(OPCodeGenerator.computeCount(1, config), 1)
    assertEquals(OPCodeGenerator.computeCount(1000000, config.copy(workerCount = 1)), 1)
    assertEquals(OPCodeGenerator.computeCount(1000000, config), 64)
    assertEquals(OPCodeGenerator.computeCount(Int.MaxValue, config.copy(workerCount = Int.MaxValue)), Int.MaxValue)
  }

  test("chunk counts use the configured factor and are capped by the input size") {
    val config = RuntimeConfig(ExecutionContext.parasitic, workerCount = 2, chunksPerWorker = 1)
    assertEquals(OPCodeGenerator.computeCount(17, config), 2)
    assertEquals(OPCodeGenerator.computeCount(17, config.copy(chunksPerWorker = 4)), 8)
    assertEquals(OPCodeGenerator.computeCount(17, config.copy(chunksPerWorker = 32)), 17)
    assertEquals(OPCodeGenerator.computeCount(17, config.copy(chunksPerWorker = Int.MaxValue)), 17)
  }
}
