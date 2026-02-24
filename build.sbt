// chiselWare versions
val coreVersion = "0.6.0"
val chiselWareVersion = "0.6.0"
val chiselWareScalaVersion = "2.13.13"

// chisel versions
val chiselVersion = "5.3.0"
val chiselTestVer = "5.0.2"

// scala versions
val scalafmtVersion = "2.5.0"
val scalaTestVer = "3.2.18"

ThisBuild / version := coreVersion
ThisBuild / scalaVersion := chiselWareScalaVersion
ThisBuild / organization := "org.chiselware"
ThisBuild / organizationName := "Chiselware"

// Scalafix settings, special configuration for test
ThisBuild / scalafixConfig := Some(baseDirectory.value / ".scalafix.conf")
ThisBuild / (Test / scalafixConfig) := Some(
  baseDirectory.value / ".scalafix-test.conf"
)

ThisBuild / scalafixOnCompile := false
ThisBuild / scalacOptions += "-Wunused:imports"
inThisBuild(
  List(
    scalaVersion := chiselWareScalaVersion,
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision
  )
)
ThisBuild / scalafixDependencies +=
  "org.chiselware" %% "chiselware-scalafix-rules" % chiselWareVersion

// pull from local temporarily
ThisBuild / scalafixDependencies +=
  "org.chiselware" %% "chiselware-scalafix-rules" % chiselWareVersion

Compile / doc / scalacOptions ++= Seq("-groups", "-implicits")

Test / parallelExecution := false

lazy val commonSettings = Seq(
  libraryDependencies ++= Seq(
    "org.chipsalliance" %% "chisel" % chiselVersion,
    "edu.berkeley.cs" %% "chiseltest" % chiselTestVer % Test,
    "org.scalatest" %% "scalatest" % scalaTestVer % Test,
    "org.chiselware" %% "chiselware-syn" % chiselWareVersion,
    "org.chiselware" %% "chiselware-ipf" % chiselWareVersion
  ),
  scalacOptions ++= Seq(
    "-language:reflectiveCalls",
    "-deprecation",
    "-feature",
    "-Xcheckinit",
    "-Ymacro-annotations"
  ),
  addCompilerPlugin(
    "org.chipsalliance" % "chisel-plugin" % chiselVersion cross
      CrossVersion.full
  )
)

lazy val root = (project in file("."))
  .aggregate(mac, pcs)
  .settings(
    name := "chiselware",
    publish / skip := true // root is an aggregator only
  )

lazy val mac = project
  .in(file("modules/mac"))
  .settings(
    name := "chiselware-core-mac",
    coverageDataDir := target.value / "../generated/scalaCoverage",
    coverageFailOnMinimum := true,
    coverageMinimumStmtTotal := 90,
    coverageMinimumBranchTotal := 95,
    publish / skip := true,
    Compile / mainClass := Some("org.chiselware.cores.o01.t001.mac.Main")
  )
  .settings(commonSettings: _*)

lazy val pcs = project
  .in(file("modules/pcs"))
  .settings(
    name := "chiselware-core-pcs",
    coverageDataDir := target.value / "../generated/scalaCoverage",
    coverageFailOnMinimum := true,
    coverageMinimumStmtTotal := 90,
    coverageMinimumBranchTotal := 95,
    publish / skip := true,
    Compile / mainClass := Some("org.chiselware.cores.o01.t001.pcs.Main")
  )
  .settings(commonSettings: _*)