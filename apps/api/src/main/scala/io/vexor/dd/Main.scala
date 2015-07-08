package io.vexor.dd

import akka.actor.ActorSystem
import scala.concurrent.Await
import scala.util.Properties
import com.typesafe.config.ConfigFactory
import akka.util.Timeout
import akka.pattern.ask

trait ApplicationEnv {
  implicit lazy val appConfig  = ConfigFactory.load(appEnv)
  implicit lazy val system     = ActorSystem(s"dd-${appEnv}", appConfig)

  def appEnv = Properties.envOrElse("APP_ENV", "development")
}

object ApplicationMain extends App with ApplicationEnv {
  models.Connector.open(appConfig.getString("cassandra.url"))

  implicit val timeout = Timeout(5 * 1000)

  val creator = system.actorOf(actors.RequestNewServerByRole.props)
  val fu1 = creator ? "default3"
  val fu2 = creator ? "default4"
  val fu3 = creator ? "default5"
  val re1 = Await.result(fu1, timeout.duration)
  val re2 = Await.result(fu2, timeout.duration)
  val re3 = Await.result(fu3, timeout.duration)

  system.awaitTermination()
}
