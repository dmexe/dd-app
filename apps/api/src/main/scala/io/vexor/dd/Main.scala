package io.vexor.dd

import akka.actor.ActorSystem
import akka.pattern.ask
import com.typesafe.config.ConfigFactory
import io.vexor.dd.actors.MainActor
import io.vexor.dd.models.DB

import scala.util.{Failure, Properties}

trait AppEnv {
  implicit lazy val system = ActorSystem(s"dd-$appEnv", appConfig)

  lazy val appConfig = ConfigFactory.load(appEnv)
  lazy val dbUrl     = appConfig.getString("cassandra.url")

  def appEnv = Properties.envOrElse("APP_ENV", "development")
}

object Main extends App with AppEnv {
  implicit val timeout = Utils.timeoutSec(5)

  val dbRe = new DB().open(dbUrl)

  dbRe match {
    case Failure(e) =>
      println(e.toString)
      System.exit(1)
    case default =>
  }

  val db : DB.Session = dbRe.get
  val mainActor = system.actorOf(MainActor.props(db), "main")

  mainActor ? MainActor.Init

  system.awaitTermination()
}
