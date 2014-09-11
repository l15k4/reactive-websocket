import sbt.Keys._
import sbt._
import utest.jsrunner.Plugin.internal.utestJsSettings
import utest.jsrunner.Plugin.internal.utestJvmSettings

import scala.scalajs.sbtplugin.ScalaJSPlugin.ScalaJSKeys._
import scala.scalajs.sbtplugin.ScalaJSPlugin._
import scala.scalajs.sbtplugin.env.phantomjs.PhantomJSEnv

import spray.revolver.RevolverPlugin._

object Build extends sbt.Build {

  val sharedSettings =
    Seq(
      organization := "com.viagraphs.websocket",
      version := "0.0.1",
      scalaVersion := "2.11.2",
      traceLevel := 0,
      resolvers += Resolver.mavenLocal,
      unmanagedSourceDirectories in Compile <+= baseDirectory(_ /  "shared" / "main" / "scala"),
      unmanagedSourceDirectories in Test <+= baseDirectory(_ / "shared" / "test" / "scala")
    )

  lazy val startTestServer = taskKey[Unit]("For scalaJS test suite")

  lazy val jvm =
    project.in(file("jvm"))
      .settings(sharedSettings: _*)
      .settings(name := "server")
      .settings(utestJvmSettings: _*)
      .settings(Revolver.settings: _*)
      .settings(
        libraryDependencies ++= Seq(
          "org.monifu" %% "monifu" % "0.13.0",
          "org.java-websocket" % "Java-WebSocket" % "1.3.1-SNAPSHOT",
          "com.lihaoyi" %% "utest" % "0.2.3" % "test",
          "com.lihaoyi" %% "upickle" % "0.2.4" % "test"
        ),
        fullClasspath in Revolver.reStart := (fullClasspath in Test).value,
        mainClass in Revolver.reStart := Option("com.viagraphs.websocket.TestingServer"),
        startTestServer := {
          (Revolver.reStart in Test).toTask("").value
        }
      )

  lazy val js =
    project.in(file("js"))
      .settings(sharedSettings: _*)
      .settings(name := "client")
      .settings(scalaJSSettings: _*)
      .settings(postLinkJSEnv := new PhantomJSEnv(autoExit = false))
      .settings(utestJsSettings: _*)
      .settings(
        libraryDependencies ++= Seq(
          "org.scala-lang.modules.scalajs" %%% "scalajs-dom" % "0.6",
          "org.monifu" %% "monifu-js" % "0.13.0",
          "com.lihaoyi" %%% "utest" % "0.2.3" % "test"
        ),
        requiresDOM := true,
        test in Test := (test in(Test, fastOptStage)).dependsOn(startTestServer in Project("jvm", file("jvm"))).value
      )

  lazy val root =
    project.in(file("."))
      .aggregate(jvm, js)
}