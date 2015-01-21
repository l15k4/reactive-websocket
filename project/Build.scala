import sbt.Keys._
import sbt._
import spray.revolver.RevolverPlugin._

import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

object Build extends sbt.Build {

  val sharedSettings =
    Seq(
      organization := "com.viagraphs",
      version := "0.0.2-SNAPSHOT",
      scalaVersion := "2.11.5",
      resolvers += Resolver.mavenLocal,
      unmanagedSourceDirectories in Compile <+= baseDirectory(_ /  "shared" / "main" / "scala"),
      unmanagedSourceDirectories in Test <+= baseDirectory(_ / "shared" / "test" / "scala"),
      scalacOptions ++= Seq(
        "-unchecked", "-deprecation", "-feature", "-Xfatal-warnings",
        "-Xlint", "-Xfuture",
        "-Yinline-warnings", "-Ywarn-adapted-args", "-Ywarn-inaccessible",
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
        <url>https://github.com/viagraphs/reactive-websocket</url>
          <licenses>
            <license>
              <name>The MIT License (MIT)</name>
              <url>http://opensource.org/licenses/MIT</url>
              <distribution>repo</distribution>
            </license>
          </licenses>
          <scm>
            <url>git@github.com:viagraphs/reactive-websocket.git</url>
            <connection>scm:git:git@github.com:viagraphs/reactive-websocket.git</connection>
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
      .settings(sharedSettings: _*)
      .settings(name := "reactive-websocket-server")
      .settings(Revolver.settings: _*)
      .settings(
        libraryDependencies ++= Seq(
          "org.monifu" %% "monifu" % "0.1-SNAPSHOT",
          "org.java-websocket" % "Java-WebSocket" % "1.3.1-SNAPSHOT",
          "com.lihaoyi" %% "upickle" % "0.2.6-RC1" % "test"
        ),
        fullClasspath in Revolver.reStart := (fullClasspath in Test).value,
        mainClass in Revolver.reStart := Option("com.viagraphs.websocket.TestingServer"),
        startTestServer := {
          (Revolver.reStart in Test).toTask("").value
        }
      )

  lazy val js =
    project.in(file("js"))
      .enablePlugins(ScalaJSPlugin)
      .settings(sharedSettings: _*)
      .settings(name := "reactive-websocket-client")
      .settings(
        libraryDependencies ++= Seq(
          "org.scala-js" %%% "scalajs-dom" % "0.7.1-SNAPSHOT",
          "org.monifu" %%% "monifu" % "0.1-SNAPSHOT",
          "com.lihaoyi" %%% "utest" % "0.2.5-RC1" % "test",
          "com.lihaoyi" %%% "upickle" % "0.2.6-RC1" % "test"
        ),
        scalaJSStage := FastOptStage,
        testFrameworks += new TestFramework("utest.runner.Framework"),
        requiresDOM := true,
        test in Test := (test in Test).dependsOn(startTestServer in Project("jvm", file("jvm"))).value
      )


  lazy val `reactive-websocket` =
    project.in(file(".")).aggregate(jvm, js)
      .settings(
        scalaVersion := "2.11.5",
        publishArtifact := false,
        publishArtifact in (Compile, packageDoc) := false,
        publishArtifact in (Compile, packageSrc) := false,
        publishArtifact in (Compile, packageBin) := false
      )
}