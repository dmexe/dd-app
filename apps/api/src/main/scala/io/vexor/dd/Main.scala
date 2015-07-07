package io.vexor.dd

import akka.actor.ActorSystem
import scala.util.Properties
import com.typesafe.config.ConfigFactory

trait ApplicationEnv {
  implicit lazy val appConfig  = ConfigFactory.load(appEnv)
  implicit lazy val system     = ActorSystem(s"dd-${appEnv}", appConfig)

  def appEnv = Properties.envOrElse("APP_ENV", "development")
}

object ApplicationMain extends App with ApplicationEnv {
  val pingActor = system.actorOf(PingActor.props, "pingActor")
  pingActor ! PingActor.Initialize
  system.awaitTermination()
}
