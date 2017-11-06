name := "kamon-mongo"

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.8"

val kamonCore         = "io.kamon"                  %%  "kamon-core"            % "1.0.0-RC1"
val kamonScala        = "io.kamon"                  %%  "kamon-scala"           % "1.0.0-RC2-23f93e2b7fa4b431136be5ab9d6fc0caceb2e13f"
val kamonTestkit      = "io.kamon"                  %%  "kamon-testkit"         % "1.0.0-RC1"

val reactiveMongo     = "org.reactivemongo"         %%  "reactivemongo"         % "0.12.7"

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
  .settings(
    libraryDependencies ++=
      compileScope(reactiveMongo, kamonCore, kamonScala) ++
        providedScope(aspectJ/*, typesafeConfig*/) ++
        testScope(kamonTestkit, logbackClassic))


import sbt.Tests._
def singleTestPerJvm(tests: Seq[TestDefinition], jvmSettings: Seq[String]): Seq[Group] =
  tests map { test =>
    Group(
      name = test.name,
      tests = Seq(test),
      runPolicy = SubProcess(ForkOptions(runJVMOptions = jvmSettings)))
  }

enableProperCrossScalaVersionTasks
