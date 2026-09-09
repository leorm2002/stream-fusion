package fuse

import scala.concurrent.ExecutionContext

/** Represent the runtime configuration, for example the execution context to use when using a parallel stream
  */
case class RuntimeConfig(ec: ExecutionContext,timeoutMillis: Long = 5000L)

object RuntimeConfig {
  given default(using ec: ExecutionContext): RuntimeConfig = RuntimeConfig(ec = ec)
}