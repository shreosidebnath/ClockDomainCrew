val chiselVersion = "3.6.0"
ThisBuild / scalaVersion := "2.13.10"
ThisBuild / dependencyOverrides += "org.scala-lang" % "scala-library" % "2.13.10"

lazy val commonSettings = Seq(
  scalaVersion := "2.13.10",
  organization := "org.chiselware",
  version := "0.1.0-SNAPSHOT"
)

lazy val root = (project in file("."))
  .settings(commonSettings)
  .aggregate(mac, pcs)
  .settings(name := "ClockDomainCrew")

lazy val mac = (project in file("modules/mac"))
  .settings(commonSettings)
  .settings(
    name := "mac",
    libraryDependencies ++= Seq(
      "edu.berkeley.cs" %% "chisel3" % chiselVersion,
      "edu.berkeley.cs" %% "chiseltest" % "0.6.0" % Test
    ),
    scalacOptions ++= Seq(
      "-language:reflectiveCalls",
      "-deprecation",
      "-feature",
      "-Xcheckinit"
    ),
    //addCompilerPlugin("org.chipsalliance" % "chisel-plugin" % "5.3.0" cross CrossVersion.full)
    addCompilerPlugin("edu.berkeley.cs" % "chisel3-plugin" % chiselVersion cross CrossVersion.full)
  )

lazy val pcs = (project in file("modules/pcs"))
  .settings(commonSettings)
  .settings(
    name := "pcs",
    libraryDependencies ++= Seq(
      "edu.berkeley.cs" %% "chisel3" % chiselVersion,
      "edu.berkeley.cs" %% "chiseltest" % "0.6.0" % Test
    ),
    scalacOptions ++= Seq(
      "-language:reflectiveCalls",
      "-deprecation",
      "-feature",
      "-Xcheckinit"
    ),
    //addCompilerPlugin("org.chipsalliance" % "chisel-plugin" % "5.3.0" cross CrossVersion.full)
    addCompilerPlugin("edu.berkeley.cs" % "chisel3-plugin" % chiselVersion cross CrossVersion.full)
  )

