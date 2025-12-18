val chiselVersion = "3.6.0"

lazy val commonSettings = Seq(
  scalaVersion := "2.13.10",
  organization := "org.chiselware",
  version := "0.1.0-SNAPSHOT"
)

lazy val root = (project in file("."))
  .settings(commonSettings)
  .aggregate(nfmac10g)
  .dependsOn(nfmac10g)
  .settings(
    name := "ClockDomainCrew"
  )

lazy val nfmac10g = (project in file("modules/nfmac10g"))
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
      "-Xcheckinit",
      "-Xfatal-warnings",
      "-Ywarn-dead-code",
      "-Ywarn-unused"
    ),
    addCompilerPlugin("edu.berkeley.cs" % "chisel3-plugin" % chiselVersion cross CrossVersion.full)
  )