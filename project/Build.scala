import sbt.Keys._
import sbt._
import spray.revolver.RevolverPlugin._

import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import org.scalajs.sbtplugin.ScalaJSPlugin.AutoImport.PhantomJSEnv

object Build extends sbt.Build {

  val sharedSettings =
    Seq(
      organization := "com.viagraphs.reactive-websocket",
      version := "0.0.2-SNAPSHOT",
      scalaVersion := "2.11.4",
      resolvers += Resolver.mavenLocal,
      unmanagedSourceDirectories in Compile <+= baseDirectory(_ /  "shared" / "main" / "scala"),
      unmanagedSourceDirectories in Test <+= baseDirectory(_ / "shared" / "test" / "scala")
    )

  lazy val startTestServer = taskKey[Unit]("For scalaJS test suite")

  lazy val jvm =
    project.in(file("jvm"))
      .settings(sharedSettings: _*)
      .settings(name := "server")
      .settings(Revolver.settings: _*)
      .settings(
        libraryDependencies ++= Seq(
          "org.monifu" %% "monifu" % "0.1-SNAPSHOT",
          "org.java-websocket" % "Java-WebSocket" % "1.3.1-SNAPSHOT",
          "com.lihaoyi" %% "upickle" % "0.2.6-M3" % "test"
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
      .settings(name := "client")
      .settings(
        libraryDependencies ++= Seq(
          "org.scala-js" %%% "scalajs-dom" % "0.7.1-SNAPSHOT",
          "org.monifu" %%% "monifu" % "0.1-SNAPSHOT",
          "com.lihaoyi" %%% "utest" % "0.2.5-M3-SNAPSHOT" % "test",
          "com.lihaoyi" %%% "upickle" % "0.2.6-M3" % "test"
        ),
        scalaJSStage := FastOptStage,
        testFrameworks += new TestFramework("utest.runner.Framework"),
        requiresDOM := true,
        test in Test := (test in Test).dependsOn(startTestServer in Project("jvm", file("jvm"))).value
      )


  lazy val `reactive-websocket` =
    project.in(file(".")).settings(scalaVersion := "2.11.4")
      .aggregate(jvm, js)
}