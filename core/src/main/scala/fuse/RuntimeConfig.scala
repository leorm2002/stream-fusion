package fuse

import scala.concurrent.ExecutionContext

/** Represent the runtime configuration, for example the execution context to use when using a parallel stream
  */
case class RuntimeConfig(ec: ExecutionContext, workerCount: Int, chunksPerWorker: Int = 8)

object RuntimeConfig {
  // Uses the default global given ExecutionContext
  given default: RuntimeConfig = RuntimeConfig(ExecutionContext.global, Runtime.getRuntime.availableProcessors())
}
