package fuse.internal

import fuse.CompileConfig
import fuse.internal.ir.AnyIR

class FusedLogger(val compileCfg: CompileConfig, val prefix: Option[String]) {

  def this(compileCfg: CompileConfig) = this(compileCfg, None)

  def of(tag: String): FusedLogger = {
    val newPrefix = prefix.fold(tag)(p => s"$p > $tag")
    new FusedLogger(compileCfg, Some(newPrefix))
  }

  def debug(msg: => String): Unit = {
    if (compileCfg.enableLogging) {
      println(format(msg))
    }
  }

  def error(msg: String): Unit = {
    println("ERROR")
    println(format(msg))
    println("ERROR")
  }

  private inline def format(msg: String): String = prefix.fold(msg)(p => s"[$p] $msg")
}
