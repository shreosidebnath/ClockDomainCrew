val chiselVersion = "3.6.0"

lazy val commonSettings = Seq(
  scalaVersion := "2.13.10",
  organization := "org.chiselware",
  version := "0.1.0-SNAPSHOT"
)

lazy val root = (project in file("."))
  .settings(commonSettings)
  .aggregate(mac, pcs, nfmac10g)
  .settings(
    name := "ClockDomainCrew"
  )

lazy val mac = (project in file("modules/nfmac10g/src/main/scala/org/chiselware/cores/o01/t001/nfmac10g"))
  .settings(commonSettings)
  .settings(
    name := "mac",
    libraryDependencies ++= Seq(
      "edu.berkeley.cs" %% "chisel3" % chiselVersion,
      "edu.berkeley.cs" %% "chiseltest" % "0.6.0" % "test"
    ),
    scalacOptions ++= Seq(
      "-language:reflectiveCalls",
      "-deprecation",
      "-feature",
      "-Xcheckinit"
    ),
    addCompilerPlugin("edu.berkeley.cs" % "chisel3-plugin" % chiselVersion cross CrossVersion.full)
  )

lazy val pcs = (project in file("modules/pcs"))
  .settings(commonSettings)
  .settings(
    name := "pcs",
    libraryDependencies ++= Seq(
      "edu.berkeley.cs" %% "chisel3" % chiselVersion,
      "edu.berkeley.cs" %% "chiseltest" % "0.6.0" % "test"
    ),
    scalacOptions ++= Seq(
      "-language:reflectiveCalls",
      "-deprecation",
      "-feature",
      "-Xcheckinit"
    ),
    addCompilerPlugin("edu.berkeley.cs" % "chisel3-plugin" % chiselVersion cross CrossVersion.full)
  )

lazy val nfmac10g = (project in file("modules/nfmac10g/src/main/scala/org/chiselware/cores/o01/t001/nfmac10g"))
  .settings(commonSettings)
  .settings(
    name := "nfmac10g",
    libraryDependencies ++= Seq(
      "edu.berkeley.cs" %% "chisel3" % chiselVersion,
      "edu.berkeley.cs" %% "chiseltest" % "0.6.0" % "test"
    ),
    scalacOptions ++= Seq(
      "-language:reflectiveCalls",
      "-deprecation",
      "-feature",
      "-Xcheckinit"
    ),
    addCompilerPlugin("edu.berkeley.cs" % "chisel3-plugin" % chiselVersion cross CrossVersion.full)
  )
