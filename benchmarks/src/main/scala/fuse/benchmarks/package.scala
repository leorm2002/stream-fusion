package fuse.benchmarks

import fuse.CompileConfig
// Configurazione per tutti i benchmark: utilizza la unsafe e no log
inline given projectWideCompileConfig: CompileConfig = CompileConfig(useUnsafe = true, enableLogging = false, strictInlining = true)

object SafeConfigs {
  inline given safeCompileConfig: CompileConfig = CompileConfig(useUnsafe = false, enableLogging = false, strictInlining = true)
}
