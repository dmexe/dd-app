package io.vexor.dd.actors

import akka.actor.{Props, ActorLogging, Actor}
import com.typesafe.config.Config
import io.vexor.dd.cloud.{AbstractCloud, DigitalOceanCloud}

import scala.util.{Failure, Success}

class CloudActor(cfg: Config) extends Actor with ActorLogging {

  import CloudActor._

  val conn = {
    DigitalOceanCloud(
      cfg.getString("cloud.digitalocean.token"),
      cfg.getString("cloud.digitalocean.region"),
      cfg.getInt("cloud.digitalocean.imageId"),
      cfg.getInt("cloud.digitalocean.keyId"),
      cfg.getString("cloud.digitalocean.size")
    )
  }

  def receive = {
    case Create(role) =>
      val re: CreateResult = conn.create(role) match {
        case Success(i) => Created(i)
        case Failure(e) => CreationFailed(e)
      }
      sender() ! re
  }
}

object CloudActor {

  case class Create(role: String)
  sealed trait CreateResult
  case class Created(instance: AbstractCloud.Instance) extends CreateResult
  case class CreationFailed(e: Throwable) extends CreateResult

  def props(cfg: Config) = Props(new CloudActor(cfg))
}