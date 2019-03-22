name := "casper-sim"

version := "0.1"

scalaVersion := "2.12.8"

libraryDependencies ++= {
  val akkaVersion = "2.5.17"

  Seq(
    "ch.qos.logback" % "logback-classic" % "1.1.7",
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
    "org.scalatest" % "scalatest_2.12" % "3.0.5" % "test"
  )
}

scalacOptions += "-Ypartial-unification"
scalacOptions += "-language:higherKinds"