scalaVersion := "2.13.10"

addCompilerPlugin("org.chipsalliance" % "chisel-plugin" % "5.1.0" cross CrossVersion.full)

libraryDependencies ++= Seq(
  "org.chipsalliance" %% "chisel" % "5.1.0"
)

scalacOptions ++= Seq(
  "-language:reflectiveCalls",
  "-deprecation",
  "-feature",
  "-Xcheckinit"
)