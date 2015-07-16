package io.vexor.cloud

import akka.pattern.ask
import io.vexor.cloud.actors.MainActor
import io.vexor.cloud.models.{NodesTable, DB}
import io.vexor.cloud.cloud.{DigitalOceanCloud, AbstractCloud}

import scala.util.Failure


object Main extends App with AppEnv {
  implicit val timeout = Utils.timeoutSec(5)

  def initDb(): DB.Session = {
    val db = new DB(dbUrl).open()

    db match {
      case Failure(e) =>
        println(e.toString)
        System.exit(1)
      case default =>
    }
    db.get
  }

  def initCloud() : AbstractCloud = {
    new DigitalOceanCloud(
      appConfig.getString("cloud.digitalocean.token"),
      appConfig.getString("cloud.digitalocean.region"),
      appConfig.getInt("cloud.digitalocean.imageId"),
      appConfig.getInt("cloud.digitalocean.keyId"),
      appConfig.getString("cloud.digitalocean.size")
    )
  }

  def initMainActor() = {
    val db    = initDb()
    val cloud = initCloud()
    system.actorOf(MainActor.props(db, cloud), "main")
  }

  val mainActor = initMainActor()

  mainActor ? MainActor.Init

  system.awaitTermination()
}
