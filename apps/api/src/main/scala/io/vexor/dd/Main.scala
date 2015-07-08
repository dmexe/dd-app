package io.vexor.dd

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vexor.dd.actors.GetReadyServer
import io.vexor.dd.models.{Connector, Server}

import scala.concurrent.Await
import scala.util.Properties

trait AppEnv {
  implicit lazy val system = ActorSystem(s"dd-$appEnv", appConfig)

  lazy val appConfig   = ConfigFactory.load(appEnv)
  lazy val databaseUrl = appConfig.getString("cassandra.url")

  def appEnv = Properties.envOrElse("APP_ENV", "development")
}

object Main extends App with AppEnv {
  implicit val timeout = Timeout(5, TimeUnit.SECONDS)

  val session = Connector(databaseUrl)
  val creator = system.actorOf(GetReadyServer.props(Server(session)))

  val fu1 = creator ? "default3"
  val fu2 = creator ? "default4"
  val fu3 = creator ? "default5"
  val re1 = Await.result(fu1, timeout.duration)
  val re2 = Await.result(fu2, timeout.duration)
  val re3 = Await.result(fu3, timeout.duration)

  system.awaitTermination()
  Connector.close(session)
}
