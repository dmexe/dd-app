name := """api"""

version := "1.0"

scalaVersion := "2.11.6"

val akkaV  = "2.3.11"
val sprayV = "1.3.2"

// enablePlugins(JDKPackagerPlugin)
enablePlugins(JavaAppPackaging)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor"       % akkaV,
  "com.typesafe.akka" %% "akka-slf4j"       % akkaV,
  "com.typesafe.akka" %% "akka-testkit"     % akkaV   % "test",

  "io.spray"          %% "spray-routing"    % sprayV,
  "io.spray"          %% "spray-can"        % sprayV,
  "io.spray"          %% "spray-json"       % sprayV,
  "io.spray"          %% "spray-testkit"    % sprayV  % "test",

  "ch.qos.logback"    %  "logback-classic"  % "1.1.3",
  "org.scalatest"     %% "scalatest"        % "2.2.4" % "test",

  "com.datastax.cassandra" % "cassandra-driver-core" % "2.1.6"
)

fork in run := true
