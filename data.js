window.BENCHMARK_DATA = {
  "lastUpdate": 1789814815455,
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
      },
      {
        "commit": {
          "author": {
            "email": "leonardo.naddei@prometeia.com",
            "name": "naddeil",
            "username": "naddeil"
          },
          "committer": {
            "email": "leonardo.naddei@prometeia.com",
            "name": "naddeil",
            "username": "naddeil"
          },
          "distinct": true,
          "id": "93c46e42aef6030cacf2f1de566574527d483abd",
          "message": "Prova dashboard benchamkrs",
          "timestamp": "2026-09-10T16:05:53+02:00",
          "tree_id": "dfe38d919d1f6c916ae75fe08fdfaa5ff34f3efa",
          "url": "https://github.com/leorm2002/stream-fusion/commit/93c46e42aef6030cacf2f1de566574527d483abd"
        },
        "date": 1789049525417,
        "tool": "jmh",
        "benches": [
          {
            "name": "fuse.benchmarks.MapToListBenchmark.fused ( {\"size\":\"1000\"} )",
            "value": 0.7523059865826062,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.fused ( {\"size\":\"100000\"} )",
            "value": 100.17148699628146,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.javaManual ( {\"size\":\"1000\"} )",
            "value": 0.8040986601719138,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.javaManual ( {\"size\":\"100000\"} )",
            "value": 89.10018394390337,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.javaManualIndexed ( {\"size\":\"1000\"} )",
            "value": 0.8358070463349515,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.javaManualIndexed ( {\"size\":\"100000\"} )",
            "value": 83.15682095413088,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.javaStream ( {\"size\":\"1000\"} )",
            "value": 4.575708305149549,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.javaStream ( {\"size\":\"100000\"} )",
            "value": 584.0311366407072,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.javaStreamOptimized ( {\"size\":\"1000\"} )",
            "value": 3.6138840219009065,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.javaStreamOptimized ( {\"size\":\"100000\"} )",
            "value": 498.93765556821074,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.scalaManual ( {\"size\":\"1000\"} )",
            "value": 2.993637784545084,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.scalaManual ( {\"size\":\"100000\"} )",
            "value": 437.8840122362455,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.scalaStream ( {\"size\":\"1000\"} )",
            "value": 5.466239159796759,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.scalaStream ( {\"size\":\"100000\"} )",
            "value": 706.0818730670682,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MegamorphicBenchmark.fusedMegamorphic ( {\"size\":\"1000\"} )",
            "value": 0.5690541100014083,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MegamorphicBenchmark.fusedMegamorphic ( {\"size\":\"100000\"} )",
            "value": 55.658441782108795,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MegamorphicBenchmark.javaManualIndexed ( {\"size\":\"1000\"} )",
            "value": 0.7120144543047849,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MegamorphicBenchmark.javaManualIndexed ( {\"size\":\"100000\"} )",
            "value": 85.14775457723101,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MegamorphicBenchmark.javaMegamorphic ( {\"size\":\"1000\"} )",
            "value": 3.3659683962013363,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MegamorphicBenchmark.javaMegamorphic ( {\"size\":\"100000\"} )",
            "value": 288.2003111276939,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MegamorphicBenchmark.javaMonomorphic ( {\"size\":\"1000\"} )",
            "value": 0.5445188899978536,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MegamorphicBenchmark.javaMonomorphic ( {\"size\":\"100000\"} )",
            "value": 57.752360385294295,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "leonardo.naddei@prometeia.com",
            "name": "naddeil",
            "username": "naddeil"
          },
          "committer": {
            "email": "leonardo.naddei@prometeia.com",
            "name": "naddeil",
            "username": "naddeil"
          },
          "distinct": true,
          "id": "8ce61526c5a37f61ddab6705f47cc4fcb0983f9e",
          "message": "Pulizia API, classi internal tutte private",
          "timestamp": "2026-09-13T01:40:36+02:00",
          "tree_id": "8d922d96db7807c19e33adce6286d4cb7f7cb5c2",
          "url": "https://github.com/leorm2002/stream-fusion/commit/8ce61526c5a37f61ddab6705f47cc4fcb0983f9e"
        },
        "date": 1789256914631,
        "tool": "jmh",
        "benches": [
          {
            "name": "fuse.benchmarks.MapToListBenchmark.fused ( {\"size\":\"1000\"} )",
            "value": 1.1420981596869657,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.fused ( {\"size\":\"100000\"} )",
            "value": 155.41252412997932,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.javaManual ( {\"size\":\"1000\"} )",
            "value": 1.1712201417871655,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.javaManual ( {\"size\":\"100000\"} )",
            "value": 150.82362062402166,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.javaManualIndexed ( {\"size\":\"1000\"} )",
            "value": 1.1103982547667253,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.javaManualIndexed ( {\"size\":\"100000\"} )",
            "value": 149.48324907009035,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.javaStream ( {\"size\":\"1000\"} )",
            "value": 8.259124337254299,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.javaStream ( {\"size\":\"100000\"} )",
            "value": 843.7592064983149,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.javaStreamOptimized ( {\"size\":\"1000\"} )",
            "value": 6.192822212771248,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.javaStreamOptimized ( {\"size\":\"100000\"} )",
            "value": 654.6362643936934,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.scalaManual ( {\"size\":\"1000\"} )",
            "value": 4.302987619506011,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.scalaManual ( {\"size\":\"100000\"} )",
            "value": 472.3820245079875,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.scalaStream ( {\"size\":\"1000\"} )",
            "value": 9.669878262563389,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.scalaStream ( {\"size\":\"100000\"} )",
            "value": 990.4536416322334,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MegamorphicBenchmark.fusedMegamorphic ( {\"size\":\"1000\"} )",
            "value": 1.0759720413028415,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MegamorphicBenchmark.fusedMegamorphic ( {\"size\":\"100000\"} )",
            "value": 180.4173931689534,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MegamorphicBenchmark.javaManualIndexed ( {\"size\":\"1000\"} )",
            "value": 1.0750532155391557,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MegamorphicBenchmark.javaManualIndexed ( {\"size\":\"100000\"} )",
            "value": 182.44709525149196,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MegamorphicBenchmark.javaMegamorphic ( {\"size\":\"1000\"} )",
            "value": 5.985540936057616,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MegamorphicBenchmark.javaMegamorphic ( {\"size\":\"100000\"} )",
            "value": 547.6333323701269,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MegamorphicBenchmark.javaMonomorphic ( {\"size\":\"1000\"} )",
            "value": 1.108334317629366,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MegamorphicBenchmark.javaMonomorphic ( {\"size\":\"100000\"} )",
            "value": 99.53260132207187,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "leonardo.naddei@prometeia.com",
            "name": "naddeil",
            "username": "naddeil"
          },
          "committer": {
            "email": "leonardo.naddei@prometeia.com",
            "name": "naddeil",
            "username": "naddeil"
          },
          "distinct": true,
          "id": "2088fbc220c00575e2c3817158cdc9cda9cd5b94",
          "message": "report",
          "timestamp": "2026-09-19T12:40:30+02:00",
          "tree_id": "dac25ee886a31f2ddecc9dc477f3815f3f35f789",
          "url": "https://github.com/leorm2002/stream-fusion/commit/2088fbc220c00575e2c3817158cdc9cda9cd5b94"
        },
        "date": 1789814815425,
        "tool": "jmh",
        "benches": [
          {
            "name": "fuse.benchmarks.MapToListBenchmark.fused ( {\"size\":\"1000\"} )",
            "value": 1.0100523574145692,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.fused ( {\"size\":\"100000\"} )",
            "value": 112.82665603486176,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.javaManual ( {\"size\":\"1000\"} )",
            "value": 1.026476839966838,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.javaManual ( {\"size\":\"100000\"} )",
            "value": 109.08554323842452,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.javaManualIndexed ( {\"size\":\"1000\"} )",
            "value": 1.013710870897511,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.javaManualIndexed ( {\"size\":\"100000\"} )",
            "value": 108.34557971966325,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.javaStream ( {\"size\":\"1000\"} )",
            "value": 6.491637635934543,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.javaStream ( {\"size\":\"100000\"} )",
            "value": 663.7986484471393,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.javaStreamOptimized ( {\"size\":\"1000\"} )",
            "value": 4.945642818004618,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.javaStreamOptimized ( {\"size\":\"100000\"} )",
            "value": 615.7478234345944,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.scalaManual ( {\"size\":\"1000\"} )",
            "value": 3.754689472937303,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.scalaManual ( {\"size\":\"100000\"} )",
            "value": 430.54295248277657,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.scalaStream ( {\"size\":\"1000\"} )",
            "value": 7.989069023545957,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MapToListBenchmark.scalaStream ( {\"size\":\"100000\"} )",
            "value": 772.4052788720063,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MegamorphicBenchmark.fusedMegamorphic ( {\"size\":\"1000\"} )",
            "value": 0.8336214045990593,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MegamorphicBenchmark.fusedMegamorphic ( {\"size\":\"100000\"} )",
            "value": 113.29083951747417,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MegamorphicBenchmark.javaManualIndexed ( {\"size\":\"1000\"} )",
            "value": 0.8860501233028778,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MegamorphicBenchmark.javaManualIndexed ( {\"size\":\"100000\"} )",
            "value": 112.94263336857775,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MegamorphicBenchmark.javaMegamorphic ( {\"size\":\"1000\"} )",
            "value": 5.006248715081235,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MegamorphicBenchmark.javaMegamorphic ( {\"size\":\"100000\"} )",
            "value": 407.77281041895895,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MegamorphicBenchmark.javaMonomorphic ( {\"size\":\"1000\"} )",
            "value": 0.9906965737437768,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          },
          {
            "name": "fuse.benchmarks.MegamorphicBenchmark.javaMonomorphic ( {\"size\":\"100000\"} )",
            "value": 88.59807192394655,
            "unit": "us/op",
            "extra": "iterations: 8\nforks: 2\nthreads: 1"
          }
        ]
      }
    ]
  }
}