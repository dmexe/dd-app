package io.vexor.cloud

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import scala.util.Properties

trait AppEnv {
  implicit lazy val system = ActorSystem(appEnv, appConfig)

  lazy val appConfig = ConfigFactory.load(appEnv)
  lazy val dbUrl     = appConfig.getString("db.url")

  def appEnv = Properties.envOrElse("APP_ENV", "development")
}
