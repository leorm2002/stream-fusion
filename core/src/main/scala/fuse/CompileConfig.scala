package fuse

import scala.quoted.*

/** Represents the configuration for the compile time generation.
  * @param strictInlining
  *   Require a named concrete receiver type and inline implementations of the collector operations used by the pipeline.
  */
case class CompileConfig(
    useUnsafe: Boolean,
    enableLogging: Boolean,
    strictInlining: Boolean
)

object CompileConfig {
  transparent inline given default: CompileConfig = CompileConfig(useUnsafe = false, enableLogging = false, strictInlining = true)

  given FromExpr[CompileConfig] with {
    def unapply(expr: Expr[CompileConfig])(using Quotes): Option[CompileConfig] = {
      expr match {
        // CompileConfig(unsafe, logging, strictInlining)
        case '{ CompileConfig($u, $l, $i) } =>
          for {
            unsafe <- u.value
            logging <- l.value
            strictInlining <- i.value
          } yield CompileConfig(unsafe, logging, strictInlining)

        case '{ FusedStream.CompileConfig($u, $l, $i) } =>
          for {
            unsafe <- u.value
            logging <- l.value
            strictInlining <- i.value
          } yield CompileConfig(unsafe, logging, strictInlining)

        // new CompileConfig(unsafe, logging, strictInlining)
        case '{ new CompileConfig($u, $l, $i) } =>
          for {
            unsafe <- u.value
            logging <- l.value
            strictInlining <- i.value
          } yield CompileConfig(unsafe, logging, strictInlining)
        case _ => None
      }
    }
  }
}
