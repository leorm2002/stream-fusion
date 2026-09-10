# Stream Fusion benchmarks

Compares `FusedStream` with Java streams.

List all benchmarks:

```text
java -jar sbt-launch.jar "benchmarks / Jmh / run -l"
```

Run a short measurement:

```text
java -jar sbt-launch.jar "benchmarks / Jmh / run -wi 1 -i 1 -f 1 -w 200ms -r 200ms -p size=100000 .*MapToListBenchmark.*"
```

Run the complete suite using the warmup, measurement, and fork annotations:

```text
java -jar sbt-launch.jar "benchmarks / Jmh / run .*Benchmark.*"
```

Add `-prof gc` to measure allocation rate and garbage-collection pressure.

## GitHub Actions

Charts use four columns on desktop, two on smaller screens, and one on mobile.
The dashboard starts with comparisons: each card groups the methods of one
benchmark class (such as `MapToListBenchmark`) with identical parameters and
measurement units. Different input sizes get separate cards. Select an execution
to compare its results; the latest execution is selected by default. Expand
**Values** for the numerical results. For execution times such as `us/op`, lower
is better; for throughput such as `ops/s`, higher is better.

The per-method history remains below the comparisons. Both views are generated
from `data.js`, so new benchmark classes and methods appear automatically.
The layout and comparison scripts in `benchmarks/site/` are applied at every
Pages deployment.

The dashboard includes a download of the historical chart data. The latest raw
JMH output is also available as
[`jmh-results.json`](https://leorm2002.github.io/stream-fusion/jmh-results.json).

To generate the same JSON locally from the repository root:

```sh
sbt -batch 'benchmarks / Jmh / compile'
sbt -batch "benchmarks / Jmh / run -foe true -rf json -rff \"$PWD/benchmarks/target/jmh-results.json\" .*Benchmark.*"
```
