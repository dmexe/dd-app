name := """api"""

version := "1.0"

scalaVersion := "2.11.6"

val akkaVersion    = "2.3.11"

// enablePlugins(JDKPackagerPlugin)
enablePlugins(JavaAppPackaging)


/*
resolvers ++= Seq(
  "Websudos releases" at "https://dl.bintray.com/websudos/oss-releases/"
)
*/

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor"       % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j"       % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit"     % akkaVersion    % "test",

  "com.datastax.cassandra" % "cassandra-driver-core" % "2.1.6",

  "ch.qos.logback"    %  "logback-classic"  % "1.1.3",
  "org.scalatest"     %% "scalatest"        % "2.2.4"  % "test"
)

fork in run := true
