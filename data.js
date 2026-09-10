window.BENCHMARK_DATA = {
  "lastUpdate": 1789046880681,
  "repoUrl": "https://github.com/leorm2002/stream-fusion",
  "entries": {
    "Stream Fusion JMH": [
      {
        "commit": {
          "author": {
            "email": "naddeileonardo@gmail.com",
            "name": "Leonardo Naddei",
            "username": "leorm2002"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "be1c912bb897dcfd8131a44668160780e908c39f",
          "message": "Merge pull request #1 from leorm2002/copilot/fix-publish-pages-job\n\nFix publish-pages failure when initializing gh-pages branch",
          "timestamp": "2026-09-10T15:21:53+02:00",
          "tree_id": "d35c2288c349ba945fc8f702e62764fdbb3dd30a",
          "url": "https://github.com/leorm2002/stream-fusion/commit/be1c912bb897dcfd8131a44668160780e908c39f"
        },
        "date": 1789046880654,
        "tool": "jmh",
        "benches": [
          {
            "name": "fuse.benchmarks.MapToListBenchmark.fused ( {\"size\":\"1000\"} )",
            "value": 1.3393105244065997,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.fused ( {\"size\":\"100000\"} )",
            "value": 156.947911943719,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.javaManual ( {\"size\":\"1000\"} )",
            "value": 1.3398576136571811,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.javaManual ( {\"size\":\"100000\"} )",
            "value": 143.70830793448428,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.javaManualIndexed ( {\"size\":\"1000\"} )",
            "value": 1.3622766014920913,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.javaManualIndexed ( {\"size\":\"100000\"} )",
            "value": 147.4462253388142,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.javaStream ( {\"size\":\"1000\"} )",
            "value": 7.705372617863083,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.javaStream ( {\"size\":\"100000\"} )",
            "value": 800.0310535708484,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.javaStreamOptimized ( {\"size\":\"1000\"} )",
            "value": 5.9335262081621805,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.javaStreamOptimized ( {\"size\":\"100000\"} )",
            "value": 632.904002189938,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.scalaManual ( {\"size\":\"1000\"} )",
            "value": 4.258300487052058,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.scalaManual ( {\"size\":\"100000\"} )",
            "value": 537.6006682603598,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.scalaStream ( {\"size\":\"1000\"} )",
            "value": 9.745550254174741,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.scalaStream ( {\"size\":\"100000\"} )",
            "value": 961.4011266984998,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MegamorphicBenchmark.fusedMegamorphic ( {\"size\":\"1000\"} )",
            "value": 0.990565317830415,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MegamorphicBenchmark.fusedMegamorphic ( {\"size\":\"100000\"} )",
            "value": 103.98486888055251,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MegamorphicBenchmark.javaManualIndexed ( {\"size\":\"1000\"} )",
            "value": 1.0495225211719095,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MegamorphicBenchmark.javaManualIndexed ( {\"size\":\"100000\"} )",
            "value": 135.51628291232407,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MegamorphicBenchmark.javaMegamorphic ( {\"size\":\"1000\"} )",
            "value": 5.975370853989082,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MegamorphicBenchmark.javaMegamorphic ( {\"size\":\"100000\"} )",
            "value": 511.36215854242516,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MegamorphicBenchmark.javaMonomorphic ( {\"size\":\"1000\"} )",
            "value": 1.1508899984145897,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MegamorphicBenchmark.javaMonomorphic ( {\"size\":\"100000\"} )",
            "value": 106.99121676267461,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          }
        ]
      }
    ]
  }
}