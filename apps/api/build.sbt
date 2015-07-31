name := """api"""

version := "1.0"

scalaVersion := "2.11.7"

val akkaV  = "2.3.12"
val sprayV = "1.3.2"

// enablePlugins(JDKPackagerPlugin)
enablePlugins(JavaAppPackaging)

javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint")

initialize := {
  val _ = initialize.value
  if (sys.props("java.specification.version") != "1.8")
    sys.error("Java 8 is required for this project.")
}

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

  "org.bouncycastle"  % "bcpkix-jdk15on"    % "1.51",
  "com.jcraft"        % "jsch"              % "0.1.53",

  "com.datastax.cassandra"   % "cassandra-driver-core"   % "2.1.6",
  "com.myjeeva.digitalocean" % "digitalocean-api-client" % "2.1"
)

fork in run := true

parallelExecution in Test := true
