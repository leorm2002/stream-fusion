package fuse

import scala.quoted.*

/** Represents the configuration for the compile time generation
  */
case class CompileConfig(
    useUnsafe: Boolean,
    enableLogging: Boolean
)

object CompileConfig {
  transparent inline given default: CompileConfig = CompileConfig(useUnsafe = false, enableLogging = false)

given FromExpr[CompileConfig] with {
    def unapply(expr: Expr[CompileConfig])(using Quotes): Option[CompileConfig] = {
      expr match {
        // CompileConfig(unsafe, logging)
        case '{ CompileConfig($u, $l) } =>
          for {
            unsafe  <- u.value
            logging <- l.value
          } yield CompileConfig(unsafe, logging)

        // new CompileConfig(unsafe, logging)
        case '{ new CompileConfig($u, $l) } =>
          for {
            unsafe  <- u.value
            logging <- l.value
          } yield CompileConfig(unsafe, logging)

        case _ => None
      }
    }
  }
}