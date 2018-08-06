name := "kamon-mongo"

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.8"

val kamonCore         = "io.kamon"                  %%  "kamon-core"            % "1.0.1"
val kamonExecutors    = "io.kamon"                  %%  "kamon-executors"       % "1.0.1"
val kamonScala        = "io.kamon"                  %%  "kamon-scala-future"    % "1.0.0"
val kamonTestkit      = "io.kamon"                  %%  "kamon-testkit"         % "1.0.1"

val dockerTestKit     = "com.whisk"                 %% "docker-testkit-scalatest" % "0.9.5"
val dockerTestKitSpotify = "com.whisk"              %% "docker-testkit-impl-spotify" % "0.9.5"

val reactiveMongo     = "org.reactivemongo"         %%  "reactivemongo"         % "0.16.0"

lazy val root = Project("kamon-mongo", file("."))
  .enablePlugins(JavaAgent)
  .settings(Seq(
    bintrayPackage := "kamon-mongo",
    moduleName := "kamon-mongo",
    scalaVersion := "2.11.8",
    crossScalaVersions := Seq("2.11.8", "2.12.3"),
    testGrouping in Test := singleTestPerJvm((definedTests in Test).value, (javaOptions in Test).value)))
  .settings(javaAgents += "org.aspectj" % "aspectjweaver"  % "1.8.10"  % "compile;test")
  .settings(resolvers += Resolver.bintrayRepo("kamon-io", "snapshots"))
  //.settings(javaOptions in Test ++= Seq("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=127.0.0.1:5005"))
  .settings(
    libraryDependencies ++=
      compileScope(reactiveMongo, kamonCore, kamonExecutors, kamonScala) ++
        providedScope(aspectJ) ++
        testScope(kamonTestkit, logbackClassic, dockerTestKit, dockerTestKitSpotify))


import sbt.Tests._
def singleTestPerJvm(tests: Seq[TestDefinition], jvmSettings: Seq[String]): Seq[Group] =
  tests map { test =>
    Group(
      name = test.name,
      tests = Seq(test),
      runPolicy = SubProcess(ForkOptions(runJVMOptions = jvmSettings)))
  }

