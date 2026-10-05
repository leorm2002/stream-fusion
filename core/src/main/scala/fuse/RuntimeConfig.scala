package fuse

import scala.concurrent.ExecutionContext

case class RuntimeConfig(ec: ExecutionContext, workerCount: Int, chunksPerWorker: Int = 8) {
  require(workerCount > 0, "workerCount must be positive")
  require(chunksPerWorker > 0, "chunksPerWorker must be positive")
}

object RuntimeConfig {
  // Uses the default global given ExecutionContext
  given default: RuntimeConfig = RuntimeConfig(ExecutionContext.global, Runtime.getRuntime.availableProcessors())
}
