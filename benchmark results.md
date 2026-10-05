# Risultati Consolidati Benchmark HotSpot Tiered Compilation (12 Lanci)

- **Data esecuzione:** 2026-10-06 01:19:12
- **Durata totale:** 0 minuti
- **Parametri:** 3 Forks, Warmup 5x2s (10s), Measurement 5x2s (10s), Heap: 8GB (-Xms8g -Xmx8g), CPU: 4 Cores (-XX:ActiveProcessorCount=4)

## Tier 0 (-XX:TieredStopAtLevel=0)

### Benchmark: monomorphic (Tier 0)

```
Benchmark                             (size)  Mode  Cnt      Score      Error  Units
MonomorphicBenchmark.fusedStream          10  avgt   15      0,553 Â±    0,009  us/op
MonomorphicBenchmark.fusedStream         100  avgt   15      2,413 Â±    0,185  us/op
MonomorphicBenchmark.fusedStream        1000  avgt   15     20,381 Â±    0,217  us/op
MonomorphicBenchmark.fusedStream      100000  avgt   15   2055,132 Â±  114,499  us/op
MonomorphicBenchmark.fusedStreamSafe      10  avgt   15      0,931 Â±    0,045  us/op
MonomorphicBenchmark.fusedStreamSafe     100  avgt   15      8,609 Â±    0,444  us/op
MonomorphicBenchmark.fusedStreamSafe    1000  avgt   15     89,522 Â±    7,466  us/op
MonomorphicBenchmark.fusedStreamSafe  100000  avgt   15   8990,055 Â±  616,904  us/op
MonomorphicBenchmark.javaManual           10  avgt   15      0,947 Â±    0,069  us/op
MonomorphicBenchmark.javaManual          100  avgt   15      8,637 Â±    0,517  us/op
MonomorphicBenchmark.javaManual         1000  avgt   15     84,939 Â±    3,697  us/op
MonomorphicBenchmark.javaManual       100000  avgt   15   7852,948 Â±  608,030  us/op
MonomorphicBenchmark.javaStream           10  avgt   15      5,032 Â±    0,441  us/op
MonomorphicBenchmark.javaStream          100  avgt   15     22,768 Â±    1,388  us/op
MonomorphicBenchmark.javaStream         1000  avgt   15    188,293 Â±    5,565  us/op
MonomorphicBenchmark.javaStream       100000  avgt   15  16264,472 Â±  580,328  us/op
MonomorphicBenchmark.scalaStream          10  avgt   15     16,474 Â±    3,303  us/op
MonomorphicBenchmark.scalaStream         100  avgt   15     88,831 Â±    5,309  us/op
MonomorphicBenchmark.scalaStream        1000  avgt   15    813,113 Â±   25,773  us/op
MonomorphicBenchmark.scalaStream      100000  avgt   15  86303,049 Â± 9126,519  us/op
```

### Benchmark: megamorphic (Tier 0)

```
Benchmark                             (size)  Mode  Cnt      Score     Error  Units
MegamorphicBenchmark.fusedStream          10  avgt   15      1,038 Â±   0,077  us/op
MegamorphicBenchmark.fusedStream         100  avgt   15      6,912 Â±   1,003  us/op
MegamorphicBenchmark.fusedStream        1000  avgt   15     61,508 Â±   3,398  us/op
MegamorphicBenchmark.fusedStream      100000  avgt   15   6251,653 Â± 407,145  us/op
MegamorphicBenchmark.fusedStreamSafe      10  avgt   15      1,364 Â±   0,069  us/op
MegamorphicBenchmark.fusedStreamSafe     100  avgt   15     11,768 Â±   0,401  us/op
MegamorphicBenchmark.fusedStreamSafe    1000  avgt   15    115,657 Â±   4,332  us/op
MegamorphicBenchmark.fusedStreamSafe  100000  avgt   15  11451,373 Â± 181,944  us/op
MegamorphicBenchmark.javaManual           10  avgt   15      1,220 Â±   0,015  us/op
MegamorphicBenchmark.javaManual          100  avgt   15     11,128 Â±   0,086  us/op
MegamorphicBenchmark.javaManual         1000  avgt   15    107,634 Â±   0,474  us/op
MegamorphicBenchmark.javaManual       100000  avgt   15  10971,256 Â± 253,405  us/op
MegamorphicBenchmark.javaStream           10  avgt   15      4,903 Â±   0,041  us/op
MegamorphicBenchmark.javaStream          100  avgt   15     16,003 Â±   0,248  us/op
MegamorphicBenchmark.javaStream         1000  avgt   15    127,953 Â±   6,643  us/op
MegamorphicBenchmark.javaStream       100000  avgt   15  11842,252 Â± 216,593  us/op
MegamorphicBenchmark.scalaStream          10  avgt   15     14,349 Â±   0,799  us/op
MegamorphicBenchmark.scalaStream         100  avgt   15     47,791 Â±   0,287  us/op
MegamorphicBenchmark.scalaStream        1000  avgt   15    369,532 Â±   1,657  us/op
MegamorphicBenchmark.scalaStream      100000  avgt   15  35947,208 Â± 543,829  us/op
```

### Benchmark: parallel_megamorphic (Tier 0)

```
Benchmark                                         (size)  Mode  Cnt     Score     Error  Units
ParallelMegamorphicBenchmark.fusedStreamParallel      10  avgt   15    34,662 Â±   0,377  us/op
ParallelMegamorphicBenchmark.fusedStreamParallel     100  avgt   15    44,291 Â±   1,092  us/op
ParallelMegamorphicBenchmark.fusedStreamParallel    1000  avgt   15    70,510 Â±   0,519  us/op
ParallelMegamorphicBenchmark.fusedStreamParallel  100000  avgt   15  3481,580 Â±  82,764  us/op
ParallelMegamorphicBenchmark.javaManualParallel       10  avgt   15    33,265 Â±   0,166  us/op
ParallelMegamorphicBenchmark.javaManualParallel      100  avgt   15    41,713 Â±   0,490  us/op
ParallelMegamorphicBenchmark.javaManualParallel     1000  avgt   15    99,216 Â±   0,789  us/op
ParallelMegamorphicBenchmark.javaManualParallel   100000  avgt   15  6747,002 Â± 100,581  us/op
ParallelMegamorphicBenchmark.javaParallelStream       10  avgt   15    46,870 Â±   0,407  us/op
ParallelMegamorphicBenchmark.javaParallelStream      100  avgt   15    62,689 Â±   1,110  us/op
ParallelMegamorphicBenchmark.javaParallelStream     1000  avgt   15   105,414 Â±   5,056  us/op
ParallelMegamorphicBenchmark.javaParallelStream   100000  avgt   15  5020,429 Â± 256,629  us/op
```

## Tier 1 (-XX:TieredStopAtLevel=1)

### Benchmark: monomorphic (Tier 1)

```
Benchmark                             (size)  Mode  Cnt     Score    Error  Units
MonomorphicBenchmark.fusedStream          10  avgt   15     0,017 Â±  0,001  us/op
MonomorphicBenchmark.fusedStream         100  avgt   15     0,109 Â±  0,003  us/op
MonomorphicBenchmark.fusedStream        1000  avgt   15     0,947 Â±  0,003  us/op
MonomorphicBenchmark.fusedStream      100000  avgt   15   101,212 Â±  3,536  us/op
MonomorphicBenchmark.fusedStreamSafe      10  avgt   15     0,019 Â±  0,001  us/op
MonomorphicBenchmark.fusedStreamSafe     100  avgt   15     0,128 Â±  0,001  us/op
MonomorphicBenchmark.fusedStreamSafe    1000  avgt   15     1,187 Â±  0,017  us/op
MonomorphicBenchmark.fusedStreamSafe  100000  avgt   15   122,622 Â±  1,771  us/op
MonomorphicBenchmark.javaManual           10  avgt   15     0,015 Â±  0,001  us/op
MonomorphicBenchmark.javaManual          100  avgt   15     0,125 Â±  0,001  us/op
MonomorphicBenchmark.javaManual         1000  avgt   15     1,165 Â±  0,004  us/op
MonomorphicBenchmark.javaManual       100000  avgt   15   117,228 Â±  0,894  us/op
MonomorphicBenchmark.javaStream           10  avgt   15     0,256 Â±  0,002  us/op
MonomorphicBenchmark.javaStream          100  avgt   15     0,975 Â±  0,004  us/op
MonomorphicBenchmark.javaStream         1000  avgt   15     7,665 Â±  0,038  us/op
MonomorphicBenchmark.javaStream       100000  avgt   15   744,804 Â±  3,318  us/op
MonomorphicBenchmark.scalaStream          10  avgt   15     0,361 Â±  0,002  us/op
MonomorphicBenchmark.scalaStream         100  avgt   15     1,928 Â±  0,008  us/op
MonomorphicBenchmark.scalaStream        1000  avgt   15    16,605 Â±  0,275  us/op
MonomorphicBenchmark.scalaStream      100000  avgt   15  1650,360 Â± 13,414  us/op
```

### Benchmark: megamorphic (Tier 1)

```
Benchmark                             (size)  Mode  Cnt     Score    Error  Units
MegamorphicBenchmark.fusedStream          10  avgt   15     0,041 Â±  0,001  us/op
MegamorphicBenchmark.fusedStream         100  avgt   15     0,372 Â±  0,011  us/op
MegamorphicBenchmark.fusedStream        1000  avgt   15     3,502 Â±  0,013  us/op
MegamorphicBenchmark.fusedStream      100000  avgt   15   351,903 Â±  1,025  us/op
MegamorphicBenchmark.fusedStreamSafe      10  avgt   15     0,041 Â±  0,001  us/op
MegamorphicBenchmark.fusedStreamSafe     100  avgt   15     0,392 Â±  0,004  us/op
MegamorphicBenchmark.fusedStreamSafe    1000  avgt   15     3,931 Â±  0,025  us/op
MegamorphicBenchmark.fusedStreamSafe  100000  avgt   15   383,883 Â±  3,203  us/op
MegamorphicBenchmark.javaManual           10  avgt   15     0,039 Â±  0,001  us/op
MegamorphicBenchmark.javaManual          100  avgt   15     0,389 Â±  0,003  us/op
MegamorphicBenchmark.javaManual         1000  avgt   15     3,803 Â±  0,015  us/op
MegamorphicBenchmark.javaManual       100000  avgt   15   384,549 Â±  3,535  us/op
MegamorphicBenchmark.javaStream           10  avgt   15     0,325 Â±  0,003  us/op
MegamorphicBenchmark.javaStream          100  avgt   15     1,154 Â±  0,013  us/op
MegamorphicBenchmark.javaStream         1000  avgt   15     8,628 Â±  0,035  us/op
MegamorphicBenchmark.javaStream       100000  avgt   15   812,151 Â±  4,153  us/op
MegamorphicBenchmark.scalaStream          10  avgt   15     0,542 Â±  0,005  us/op
MegamorphicBenchmark.scalaStream         100  avgt   15     2,209 Â±  0,051  us/op
MegamorphicBenchmark.scalaStream        1000  avgt   15    18,155 Â±  0,217  us/op
MegamorphicBenchmark.scalaStream      100000  avgt   15  1761,645 Â± 24,225  us/op
```

### Benchmark: parallel_megamorphic (Tier 1)

```
Benchmark                                         (size)  Mode  Cnt    Score   Error  Units
ParallelMegamorphicBenchmark.fusedStreamParallel      10  avgt   15   12,860 Â± 1,145  us/op
ParallelMegamorphicBenchmark.fusedStreamParallel     100  avgt   15   13,247 Â± 0,827  us/op
ParallelMegamorphicBenchmark.fusedStreamParallel    1000  avgt   15   15,942 Â± 1,039  us/op
ParallelMegamorphicBenchmark.fusedStreamParallel  100000  avgt   15  122,676 Â± 1,164  us/op
ParallelMegamorphicBenchmark.javaManualParallel       10  avgt   15   13,002 Â± 1,448  us/op
ParallelMegamorphicBenchmark.javaManualParallel      100  avgt   15   14,045 Â± 0,292  us/op
ParallelMegamorphicBenchmark.javaManualParallel     1000  avgt   15   15,559 Â± 0,349  us/op
ParallelMegamorphicBenchmark.javaManualParallel   100000  avgt   15  214,144 Â± 0,750  us/op
ParallelMegamorphicBenchmark.javaParallelStream       10  avgt   15    8,646 Â± 0,780  us/op
ParallelMegamorphicBenchmark.javaParallelStream      100  avgt   15   11,485 Â± 0,742  us/op
ParallelMegamorphicBenchmark.javaParallelStream     1000  avgt   15   16,172 Â± 0,555  us/op
ParallelMegamorphicBenchmark.javaParallelStream   100000  avgt   15  245,960 Â± 1,933  us/op
```

## Tier 4 (-XX:TieredStopAtLevel=4)

### Benchmark: monomorphic (Tier 4)

```
Benchmark                             (size)  Mode  Cnt    Score    Error  Units
MonomorphicBenchmark.fusedStream          10  avgt   15    0,006 Â±  0,001  us/op
MonomorphicBenchmark.fusedStream         100  avgt   15    0,067 Â±  0,001  us/op
MonomorphicBenchmark.fusedStream        1000  avgt   15    0,897 Â±  0,002  us/op
MonomorphicBenchmark.fusedStream      100000  avgt   15   92,760 Â±  0,224  us/op
MonomorphicBenchmark.fusedStreamSafe      10  avgt   15    0,007 Â±  0,001  us/op
MonomorphicBenchmark.fusedStreamSafe     100  avgt   15    0,067 Â±  0,001  us/op
MonomorphicBenchmark.fusedStreamSafe    1000  avgt   15    0,896 Â±  0,001  us/op
MonomorphicBenchmark.fusedStreamSafe  100000  avgt   15   94,040 Â±  1,072  us/op
MonomorphicBenchmark.javaManual           10  avgt   15    0,007 Â±  0,001  us/op
MonomorphicBenchmark.javaManual          100  avgt   15    0,067 Â±  0,001  us/op
MonomorphicBenchmark.javaManual         1000  avgt   15    0,896 Â±  0,002  us/op
MonomorphicBenchmark.javaManual       100000  avgt   15   92,869 Â±  0,257  us/op
MonomorphicBenchmark.javaStream           10  avgt   15    0,067 Â±  0,001  us/op
MonomorphicBenchmark.javaStream          100  avgt   15    0,451 Â±  0,007  us/op
MonomorphicBenchmark.javaStream         1000  avgt   15    4,279 Â±  0,007  us/op
MonomorphicBenchmark.javaStream       100000  avgt   15  428,277 Â±  1,573  us/op
MonomorphicBenchmark.scalaStream          10  avgt   15    0,041 Â±  0,001  us/op
MonomorphicBenchmark.scalaStream         100  avgt   15    0,317 Â±  0,005  us/op
MonomorphicBenchmark.scalaStream        1000  avgt   15    3,115 Â±  0,023  us/op
MonomorphicBenchmark.scalaStream      100000  avgt   15  320,285 Â±  4,191  us/op
```

### Benchmark: megamorphic (Tier 4)

```
Benchmark                             (size)  Mode  Cnt    Score    Error  Units
MegamorphicBenchmark.fusedStream          10  avgt   15    0,011 Â±  0,001  us/op
MegamorphicBenchmark.fusedStream         100  avgt   15    0,082 Â±  0,001  us/op
MegamorphicBenchmark.fusedStream        1000  avgt   15    1,267 Â±  0,004  us/op
MegamorphicBenchmark.fusedStream      100000  avgt   15  114,744 Â±  2,171  us/op
MegamorphicBenchmark.fusedStreamSafe      10  avgt   15    0,012 Â±  0,001  us/op
MegamorphicBenchmark.fusedStreamSafe     100  avgt   15    0,083 Â±  0,002  us/op
MegamorphicBenchmark.fusedStreamSafe    1000  avgt   15    1,272 Â±  0,010  us/op
MegamorphicBenchmark.fusedStreamSafe  100000  avgt   15  117,412 Â±  1,863  us/op
MegamorphicBenchmark.javaManual           10  avgt   15    0,011 Â±  0,001  us/op
MegamorphicBenchmark.javaManual          100  avgt   15    0,082 Â±  0,001  us/op
MegamorphicBenchmark.javaManual         1000  avgt   15    1,260 Â±  0,003  us/op
MegamorphicBenchmark.javaManual       100000  avgt   15  115,942 Â±  1,505  us/op
MegamorphicBenchmark.javaStream           10  avgt   15    0,084 Â±  0,008  us/op
MegamorphicBenchmark.javaStream          100  avgt   15    0,511 Â±  0,026  us/op
MegamorphicBenchmark.javaStream         1000  avgt   15    4,688 Â±  0,141  us/op
MegamorphicBenchmark.javaStream       100000  avgt   15  474,972 Â± 17,787  us/op
MegamorphicBenchmark.scalaStream          10  avgt   15    0,062 Â±  0,001  us/op
MegamorphicBenchmark.scalaStream         100  avgt   15    0,492 Â±  0,011  us/op
MegamorphicBenchmark.scalaStream        1000  avgt   15    4,943 Â±  0,337  us/op
MegamorphicBenchmark.scalaStream      100000  avgt   15  631,320 Â±  4,161  us/op
```

### Benchmark: parallel_megamorphic (Tier 4)

```
Benchmark                                         (size)  Mode  Cnt    Score   Error  Units
ParallelMegamorphicBenchmark.fusedStreamParallel      10  avgt   15   11,221 Â± 0,969  us/op
ParallelMegamorphicBenchmark.fusedStreamParallel     100  avgt   15   11,893 Â± 1,009  us/op
ParallelMegamorphicBenchmark.fusedStreamParallel    1000  avgt   15   14,037 Â± 0,455  us/op
ParallelMegamorphicBenchmark.fusedStreamParallel  100000  avgt   15   48,199 Â± 0,318  us/op
ParallelMegamorphicBenchmark.javaManualParallel       10  avgt   15   11,077 Â± 0,780  us/op
ParallelMegamorphicBenchmark.javaManualParallel      100  avgt   15   11,976 Â± 0,951  us/op
ParallelMegamorphicBenchmark.javaManualParallel     1000  avgt   15   13,345 Â± 0,505  us/op
ParallelMegamorphicBenchmark.javaManualParallel   100000  avgt   15   66,263 Â± 0,770  us/op
ParallelMegamorphicBenchmark.javaParallelStream       10  avgt   15    3,772 Â± 0,051  us/op
ParallelMegamorphicBenchmark.javaParallelStream      100  avgt   15    5,124 Â± 0,220  us/op
ParallelMegamorphicBenchmark.javaParallelStream     1000  avgt   15    8,668 Â± 0,516  us/op
ParallelMegamorphicBenchmark.javaParallelStream   100000  avgt   15  148,636 Â± 3,138  us/op
```

