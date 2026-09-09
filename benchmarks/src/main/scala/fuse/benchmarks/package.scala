package fuse.benchmarks

import fuse.CompileConfig
// Configurazione per tutti i benchmark: utilizza la unsafe e no log
inline given projectWideCompileConfig: CompileConfig = CompileConfig(useUnsafe = true, enableLogging = false)
