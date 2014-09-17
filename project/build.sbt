
resolvers += Resolver.sonatypeRepo("releases")

resolvers += Resolver.sonatypeRepo("snapshots")

addSbtPlugin("org.scala-lang.modules.scalajs" % "scalajs-sbt-plugin" % "0.5.5-SNAPSHOT")

addSbtPlugin("io.spray" % "sbt-revolver" % "0.7.2")