ThisBuild / scalaVersion     := "2.12.18"
ThisBuild / version          := "0.1.0"
ThisBuild / organization     := "com.github.benja"

val chiselVersion = "3.6.0"

lazy val root = (project in file("."))
  .settings(
    name := "ethernet-mac-chisel",
    libraryDependencies ++= Seq(
      "edu.berkeley.cs" %% "chisel3"    % chiselVersion,
      "edu.berkeley.cs" %% "chiseltest" % "0.6.0" % "test"Microsoft Windows [Version 10.0.26100.7623]
(c) Microsoft Corporation. All rights reserved.

C:\Users\benja\ethernet-mac-chisel>sbt "runMain MacMain"
WARNING: An illegal reflective access operation has occurred
WARNING: Illegal reflective access by org.jline.terminal.impl.exec.ExecTerminalProvider$ReflectionRedirectPipeCreator (file:/C:/Users/benja/.sbt/boot/scala-2.12.20/org.scala-sbt/sbt/1.11.4/jline-terminal-3.27.1.jar) to constructor java.lang.ProcessBuilder$RedirectPipeImpl()
WARNING: Please consider reporting this to the maintainers of org.jline.terminal.impl.exec.ExecTerminalProvider$ReflectionRedirectPipeCreator
WARNING: Use --illegal-access=warn to enable warnings of further illegal reflective access operations
WARNING: All illegal access operations will be denied in a future release
[info] welcome to sbt 1.11.4 (Eclipse Adoptium Java 11.0.28)
[info] loading settings for project ethernet-mac-chisel-build-build from metals.sbt...
[info] loading project definition from C:\Users\benja\ethernet-mac-chisel\project\project
[info] loading settings for project ethernet-mac-chisel-build from metals.sbt...
[info] loading project definition from C:\Users\benja\ethernet-mac-chisel\project
[info] loading settings for project root from build.sbt...
[info] set current project to ethernet-mac-chisel (in build file:/C:/Users/benja/ethernet-mac-chisel/)
[info] Updating ethernet-mac-chisel_2.12
[info] Resolved ethernet-mac-chisel_2.12 dependencies
[warn]
[warn]  Note: Unresolved dependencies path:                                                                                
[error] sbt.librarymanagement.ResolveException: Error downloading edu.berkeley.cs:chisel3-plugin_2.12.18:3.6.0
[error]   Not found
[error]   Not found
[error]   not found: C:\Users\benja\.ivy2\localedu.berkeley.cs\chisel3-plugin_2.12.18\3.6.0\ivys\ivy.xml
[error]   not found: https://repo1.maven.org/maven2/edu/berkeley/cs/chisel3-plugin_2.12.18/3.6.0/chisel3-plugin_2.12.18-3.6.0.pom
[error]         at lmcoursier.CoursierDependencyResolution.unresolvedWarningOrThrow(CoursierDependencyResolution.scala:347 
[error]         at lmcoursier.CoursierDependencyResolution.$anonfun$update$39(CoursierDependencyResolution.scala:316)      
[error]         at scala.util.Either$LeftProjection.map(Either.scala:573)
[error]         at lmcoursier.CoursierDependencyResolution.update(CoursierDependencyResolution.scala:316)
[error]         at sbt.librarymanagement.DependencyResolution.update(DependencyResolution.scala:60)
[error]         at sbt.internal.LibraryManagement$.resolve$1(LibraryManagement.scala:60)
[error]         at sbt.internal.LibraryManagement$.$anonfun$cachedUpdate$12(LibraryManagement.scala:142)
[error]         at sbt.util.Tracked$.$anonfun$lastOutput$1(Tracked.scala:74)
[error]         at sbt.internal.LibraryManagement$.$anonfun$cachedUpdate$11(LibraryManagement.scala:144)
[error]         at sbt.internal.LibraryManagement$.$anonfun$cachedUpdate$11$adapted(LibraryManagement.scala:131)
[error]         at sbt.util.Tracked$.$anonfun$inputChangedW$1(Tracked.scala:220)
[error]         at sbt.internal.LibraryManagement$.cachedUpdate(LibraryManagement.scala:169)
[error]         at sbt.Classpaths$.$anonfun$updateTask0$1(Defaults.scala:3975)
[error]         at scala.Function1.$anonfun$compose$1(Function1.scala:49)
[error]         at sbt.internal.util.$tilde$greater.$anonfun$$u2219$1(TypeFunctions.scala:63)
[error]         at sbt.std.Transform$$anon$4.work(Transform.scala:69)
[error]         at sbt.Execute.$anonfun$submit$2(Execute.scala:283)
[error]         at sbt.internal.util.ErrorHandling$.wideConvert(ErrorHandling.scala:24)
[error]         at sbt.Execute.work(Execute.scala:292)
[error]         at sbt.Execute.$anonfun$submit$1(Execute.scala:283)
[error]         at sbt.ConcurrentRestrictions$$anon$4.$anonfun$submitValid$1(ConcurrentRestrictions.scala:265)
[error]         at sbt.CompletionService$$anon$2.call(CompletionService.scala:65)
[error]         at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
[error]         at java.base/java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:515)
[error]         at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
[error]         at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128)
[error]         at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:628)
[error]         at java.base/java.lang.Thread.run(Thread.java:829)
[error] (update) sbt.librarymanagement.ResolveException: Error downloading edu.berkeley.cs:chisel3-plugin_2.12.18:3.6.0    
[error]   Not found
[error]   Not found
[error]   not found: C:\Users\benja\.ivy2\localedu.berkeley.cs\chisel3-plugin_2.12.18\3.6.0\ivys\ivy.xml
[error]   not found: https://repo1.maven.org/maven2/edu/berkeley/cs/chisel3-plugin_2.12.18/3.6.0/chisel3-plugin_2.12.18-3.6.0.pom
[error] Total time: 3 s, completed Jan. 29, 2026, 3:00:35 p.m.
    ),
    // Use single % for the plugin to match the Berkeley repository structure
   // addCompilerPlugin("edu.berkeley.cs" % "chisel3-plugin" % chiselVersion cross CrossVersion.full)
  )