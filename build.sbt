val scala3Version = "3.8.4"
val projectVesion = "0.1.0-SNAPSHOT"

val commonSettings = Seq(
  scalaVersion := scala3Version,
  scalacOptions ++= Seq(
    "-no-indent",
    "-old-syntax"
  )
)
ThisBuild / scalaVersion := scala3Version

ThisBuild / scalacOptions ++= Seq(
  "-no-indent",
  "-old-syntax"
)

// 1. Root meta-project
lazy val root = project
  .in(file("."))
  .aggregate(core, runner, benchmarks)
  .settings(
    name := "stream-fusion-root"
  )

// 2. Core macro library module
lazy val core = project
  .in(file("core"))
  .settings(
    commonSettings,
    name := "stream-fusion-core",
    version := projectVesion,
    libraryDependencies += "org.scalameta" %% "munit" % "1.0.0" % Test,
    libraryDependencies += "org.scala-lang" %% "scala3-staging" % scalaVersion.value % Test
  )

// 3. Application module that uses the macro
lazy val runner = project
  .in(file("runner"))
  .dependsOn(core) // ensures 'core'
  .settings(
    commonSettings,
    name := "stream-fusion-runner",
    version := projectVesion
  )

// 4. JMH benchmarks comparing fused pipelines with classic Java streams
lazy val benchmarks = project
  .in(file("benchmarks"))
  .dependsOn(core)
  .enablePlugins(JmhPlugin)
  .settings(
    commonSettings,
    name := "stream-fusion-benchmarks",
    version := projectVesion,
    libraryDependencies += "org.scalameta" %% "munit" % "1.0.0" % Test,
    publish / skip := true,
    Jmh / javaOptions += "--add-opens=java.base/java.util=ALL-UNNAMED"
  )
