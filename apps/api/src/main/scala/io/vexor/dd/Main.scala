package io.vexor.dd

import akka.pattern.ask
import io.vexor.dd.actors.MainActor
import io.vexor.dd.models.{NodesTable, DB}
import io.vexor.dd.cloud.{DigitalOceanCloud, AbstractCloud}

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

  def initNodesTable(db: DB.Session) = {
    val nodesTable = NodesTable(db)
    nodesTable.up()
    nodesTable
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
    val db         = initDb()
    val nodesTable = initNodesTable(db)
    system.actorOf(MainActor.props(nodesTable), "main")
  }

  val mainActor = initMainActor()

  mainActor ? MainActor.Init

  system.awaitTermination()
}
