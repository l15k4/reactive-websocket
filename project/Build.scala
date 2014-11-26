import sbt.Keys._
import sbt._
import spray.revolver.RevolverPlugin._

import scala.scalajs.sbtplugin.ScalaJSPlugin.ScalaJSKeys._
import scala.scalajs.sbtplugin.ScalaJSPlugin._
import scala.scalajs.sbtplugin.env.phantomjs.PhantomJSEnv
import utest.jsrunner.Plugin.internal.utestJsSettings

object Build extends sbt.Build {

  val sharedSettings =
    Seq(
      organization := "com.viagraphs.reactive-websocket",
      version := "0.0.1",
      scalaVersion := "2.11.2",
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
          "org.monifu" %% "monifu" % "0.14.1",
          "org.java-websocket" % "Java-WebSocket" % "1.3.1-SNAPSHOT",
          "com.lihaoyi" %% "upickle" % "0.2.6-SNAPSHOT" % "test"
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
      .settings(utestJsSettings: _*)
      .settings(
        libraryDependencies ++= Seq(
          "org.scala-lang.modules.scalajs" %%% "scalajs-dom" % "0.7-SNAPSHOT",
          "org.monifu" %%% "monifu-rx-js" % "0.14.1",
          "com.lihaoyi" %%% "utest" % "0.2.6-SNAPSHOT" % "test",
          "com.lihaoyi" %%% "upickle" % "0.2.6-SNAPSHOT" % "test"
        ),
        requiresDOM := true,
        postLinkJSEnv in Test := new PhantomJSEnv(autoExit = false),
        test in Test := (test in(Test, fastOptStage)).dependsOn(startTestServer in Project("jvm", file("jvm"))).value
      )


  lazy val `reactive-websocket` =
    project.in(file(".")).settings(scalaVersion := "2.11.2")
      .aggregate(jvm, js)
}