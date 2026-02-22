ThisBuild / scalaVersion := "2.13.13"
ThisBuild / version := "0.1.0"
ThisBuild / organization := "org.chiselware"
ThisBuild / organizationName := "Chiselware"

Compile / doc / scalacOptions ++= Seq("-groups", "-implicits")

Test / parallelExecution := false

val chiselVersion = "5.3.0"
val chiselTestVer = "5.0.2"
val scalafmtVersion = "2.5.0"
val scalaTestVer = "3.2.18"

lazy val commonSettings = Seq(
  libraryDependencies ++= Seq(
    "org.chipsalliance" %% "chisel" % chiselVersion,
    "edu.berkeley.cs" %% "chiseltest" % chiselTestVer % Test,
    "org.scalatest" %% "scalatest" % scalaTestVer % Test,
    "org.chiselware" %% "chiselware-syn" % "0.1.0"
  ),
  scalacOptions ++= Seq(
    "-language:reflectiveCalls",
    "-deprecation",
    "-feature",
    "-Xcheckinit",
    "-Ymacro-annotations"
  ),
  addCompilerPlugin(
    "org.chipsalliance" % "chisel-plugin" % chiselVersion cross CrossVersion.full
  )
)

lazy val root = (project in file("."))
  .aggregate(core)
  .settings(
    name := "chiselware",
    publish / skip := true // root is an aggregator only
  )

lazy val core = project
  .in(file("modules/nfmac10g"))
  .settings(
    name := "chiselware-core-nfmac10g",
    coverageDataDir := target.value / "../generated/scalaCoverage",
    coverageFailOnMinimum := true,
    coverageMinimumStmtTotal := 90,
    coverageMinimumBranchTotal := 95,
    publish / skip := true,
    Compile / mainClass := Some("org.chiselware.cores.o01.t001.mac.tx.Main")
  )
  .settings(commonSettings: _*)