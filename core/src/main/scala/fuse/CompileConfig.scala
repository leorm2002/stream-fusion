package fuse

import scala.quoted.*

/** Represents the configuration for the compile-time code generation.
  *
  * NOTE: Instances of `CompileConfig` used with `FusedStream.collect` MUST be supplied as compile-time inline constants with literal boolean arguments (e.g.
  * `CompileConfig(useUnsafe = false, enableLogging = false, strictInlining = true)`). Non-literal values cannot be extracted by the macro and will emit a compilation error.
  *
  * @param useUnsafe
  *   Use VarHandle to read java.util.ArrayList's internal array directly (requires `--add-opens java.base/java.util=ALL-UNNAMED`).
  * @param enableLogging
  *   Print compiler debug messages and generated code during macro expansion.
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
