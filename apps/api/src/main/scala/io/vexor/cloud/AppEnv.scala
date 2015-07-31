package io.vexor.cloud

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import scala.util.Properties

trait AppEnv {
  implicit lazy val system = ActorSystem(appEnv, appConfig)

  lazy val appConfig = ConfigFactory.load(appEnv)

  def appEnv       = Properties.envOrElse("APP_ENV",           "development")
  def caDir        = Properties.envOrElse("APP_CA_DIR",        "ca")
  def cloudInitDir = Properties.envOrElse("APP_CLOUDINIT_DIR", "cloudinit")
  def clientCa     = Properties.envOrElse("CLIENT_CA",         "ca")
  def clientCaPass = Properties.envOrElse("CLIENT_CA_PASS",    "foobar")
}
