/*
scalaVersion := "2.13.10"

libraryDependencies += "edu.berkeley.cs" %% "chisel3" % "3.6.0"
libraryDependencies += "edu.berkeley.cs" %% "chiseltest" % "0.6.0"
*/

ThisBuild / scalaVersion     := "2.13.13"
ThisBuild / version          := "0.1.0"
ThisBuild / organization     := "ca.clockdomaincrew"
ThisBuild / organizationName := "Clock Domain Crew"

val chiselVersion   = "5.3.0"
val chiselTestVer   = "5.0.2"

lazy val root = (project in file("."))
  .settings(
    name := "ClockDomainCrew",
    libraryDependencies ++= Seq(
      "org.chipsalliance" %% "chisel"     % chiselVersion,
      "edu.berkeley.cs"   %% "chiseltest" % chiselTestVer % Test
    ),
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-Xcheckinit",
      "-Ymacro-annotations"
    ),
    addCompilerPlugin(
      "org.chipsalliance" % "chisel-plugin" % chiselVersion cross CrossVersion.full
    )
  )
