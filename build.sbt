import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import spray.revolver.RevolverPlugin.autoImport._

val sharedSettings =
  Seq(
    organization := "com.pragmaxim",
    version := "0.0.4-SNAPSHOT",
    scalaVersion := "2.12.10",
    resolvers += Resolver.mavenLocal,
    unmanagedSourceDirectories in Compile += baseDirectory(_ /  "shared" / "main" / "scala").value,
    unmanagedSourceDirectories in Test += baseDirectory(_ / "shared" / "test" / "scala").value,
    scalacOptions ++= Seq(
      "-unchecked", "-deprecation", "-feature", "-Xfatal-warnings",
      "-Xlint", "-Xfuture",
      "-Ywarn-adapted-args", "-Ywarn-inaccessible",
      "-Ywarn-nullary-override", "-Ywarn-nullary-unit", "-Yno-adapted-args"
    ),
    publishMavenStyle := true,
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false },
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value)
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },
    pomExtra :=
      <url>https://github.com/l15k4/reactive-websocket</url>
        <licenses>
          <license>
            <name>The MIT License (MIT)</name>
            <url>http://opensource.org/licenses/MIT</url>
            <distribution>repo</distribution>
          </license>
        </licenses>
        <scm>
          <url>git@github.com:l15k4/reactive-websocket.git</url>
          <connection>scm:git:git@github.com:l15k4/reactive-websocket.git</connection>
        </scm>
        <developers>
          <developer>
            <id>l15k4</id>
            <name>Jakub Liska</name>
            <email>liska.jakub@gmail.com</email>
          </developer>
        </developers>
  )

lazy val startTestServer = taskKey[Unit]("For scalaJS test suite")

lazy val jvm =
  project.in(file("jvm"))
    .enablePlugins(RevolverPlugin)
    .settings(sharedSettings: _*)
    .settings(name := "reactive-websocket-server")
    .settings(Revolver.settings: _*)
    .settings(
      libraryDependencies ++= Seq(
        "io.monix" %% "monix" % "3.1.0",
        "org.java-websocket" % "Java-WebSocket" % "1.4.0",
        "com.lihaoyi" %% "upickle" % "0.9.5" % "test"
      ),
      fullClasspath in reStart := (fullClasspath in Test).value,
      mainClass in reStart := Option("com.pragmaxim.websocket.TestingServer"),
      startTestServer := {
        (reStart in Test).toTask("").value
      }
    )

lazy val js =
  project.in(file("js"))
    .enablePlugins(ScalaJSPlugin, RevolverPlugin)
    .settings(sharedSettings: _*)
    .settings(name := "reactive-websocket-client")
    .settings(
      libraryDependencies ++= Seq(
        "org.scala-js" %%% "scalajs-dom" % "0.9.8",
        "io.monix" %%% "monix" % "3.1.0",
        "com.lihaoyi" %%% "utest" % "0.7.1" % "test",
        "com.lihaoyi" %%% "upickle" % "0.9.5" % "test"
      ),
      scalaJSStage := FastOptStage,
      testFrameworks += new TestFramework("utest.runner.Framework"),
      requiresDOM := true,
      test in Test := (test in Test).dependsOn(startTestServer in Project("jvm", file("jvm"))).value
    )


lazy val `reactive-websocket` =
  project.in(file(".")).aggregate(jvm, js)
    .settings(
      scalaVersion := "2.12.10",
      publishArtifact := false,
      publishArtifact in (Compile, packageDoc) := false,
      publishArtifact in (Compile, packageSrc) := false,
      publishArtifact in (Compile, packageBin) := false
    )
