// Minimum Scala 3 version required by consumers due to Quotes/Reflection macro capabilities
val scala3Version = "3.8.4"
val projectVersion = "0.1.1"

ThisBuild / organization := "it.ln.stream-fusion"
ThisBuild / organizationName := "stream-fusion"
ThisBuild / version := projectVersion
ThisBuild / scalaVersion := scala3Version
ThisBuild / scalacOptions ++= Seq(
  "-no-indent",
  "-old-syntax"
)
ThisBuild / licenses := List("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.txt"))
ThisBuild / homepage := Some(url("https://github.com/leorm2002/stream-fusion"))
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/leorm2002/stream-fusion"),
    "scm:git:git@github.com:leorm2002/stream-fusion.git"
  )
)

// Root meta-project
lazy val root = project
  .in(file("."))
  .aggregate(core, benchmarks)
  .settings(
    name := "stream-fusion-root",
    publish / skip := true
  )

// Core macro library module
lazy val core = project
  .in(file("core"))
  .settings(
    name := "stream-fusion-core",
    Test / fork := true,
    Test / javaOptions += "--add-opens=java.base/java.util=ALL-UNNAMED",
    libraryDependencies += "org.scalameta" %% "munit" % "1.0.0" % Test,
    libraryDependencies += "org.scala-lang" %% "scala3-staging" % scalaVersion.value % Test
  )

// JMH benchmarks
lazy val benchmarks = project
  .in(file("benchmarks"))
  .dependsOn(core)
  .enablePlugins(JmhPlugin)
  .settings(
    name := "stream-fusion-benchmarks",
    libraryDependencies += "org.scalameta" %% "munit" % "1.0.0" % Test,
    publish / skip := true,
    Jmh / javaOptions += "--add-opens=java.base/java.util=ALL-UNNAMED"
  )
