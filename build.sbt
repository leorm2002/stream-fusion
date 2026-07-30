val scala3Version = "3.8.4"

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
  .aggregate(core, runner)
  .settings(
    name := "stream-fusion-root"
  )

// 2. Core macro library module
lazy val core = project
  .in(file("core"))
  .settings(
    commonSettings,
    name := "stream-fusion-core",
    version := "0.1.0-SNAPSHOT",
    libraryDependencies += "org.scalameta" %% "munit" % "1.0.0" % Test
  )

// 3. Application module that uses the macro
lazy val runner = project
  .in(file("runner"))
  .dependsOn(core) // <-- Crucial: ensures 'core' compiles FIRST
  .settings(
    commonSettings,
    name := "stream-fusion-runner",
    version := "0.1.0-SNAPSHOT"
  )
