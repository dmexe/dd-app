package io.vexor.dd


import akka.actor.ActorSystem
import akka.io.IO
import akka.pattern.ask
import com.typesafe.config.ConfigFactory
import spray.can.Http

import scala.util.Properties

trait AppEnv {
  implicit lazy val system = ActorSystem(s"dd-$appEnv", appConfig)

  lazy val appConfig = ConfigFactory.load(appEnv)
  lazy val dbUrl     = appConfig.getString("cassandra.url")

  def appEnv = Properties.envOrElse("APP_ENV", "development")
}

object Main extends App with AppEnv {
  implicit val timeout = Utils.timeoutSec(5)

  val mainActor = system.actorOf(actors.Main.props(this), "main")
  val httpActor = system.actorOf(handlers.Nodes.HttpHandler.props, "http")

  mainActor ? actors.Main.Init
  IO(Http) ? Http.Bind(httpActor, interface = "localhost", port = 3000)

  system.awaitTermination()
}
