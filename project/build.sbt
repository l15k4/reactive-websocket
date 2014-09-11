
resolvers += Resolver.sonatypeRepo("releases")

resolvers += Resolver.sonatypeRepo("snapshots")

addSbtPlugin("org.scala-lang.modules.scalajs" % "scalajs-sbt-plugin" % "0.5.4")

addSbtPlugin("com.lihaoyi" % "utest-js-plugin" % "0.2.3")

addSbtPlugin("io.spray" % "sbt-revolver" % "0.7.2")