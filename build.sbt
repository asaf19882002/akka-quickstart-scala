name := "akka-quickstart-scala"

version := "1.0"

scalaVersion := "2.13.1"

lazy val akkaVersion = "2.6.0-RC1"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
  "org.scalatest" %% "scalatest" % "3.0.8" % Test
)
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.1.3" % Runtime