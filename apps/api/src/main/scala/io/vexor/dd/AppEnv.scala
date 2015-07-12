package io.vexor.dd

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import scala.util.Properties

trait AppEnv {
  implicit lazy val system = ActorSystem(s"dd-$appEnv", appConfig)

  lazy val appConfig = ConfigFactory.load(appEnv)
  lazy val dbUrl     = appConfig.getString("cassandra.url")

  def appEnv = Properties.envOrElse("APP_ENV", "development")
}
