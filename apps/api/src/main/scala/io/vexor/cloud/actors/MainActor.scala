package io.vexor.cloud.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.Config
import io.vexor.cloud.cloud.{AbstractCloud, DigitalOceanCloud}
import io.vexor.cloud.handlers.HttpHandler
import io.vexor.cloud.models.ModelRegistry
import spray.can.Http

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}

object MainActor {
  sealed trait Command
  object Command {
    case object Start extends Command
  }

  sealed trait StartReply
  case object StartSuccess extends StartReply
  case class  StartFailure(e: String) extends StartReply

  def props(cfg: Config) : Props = Props(new MainActor(cfg))
}

class MainActor(cfg: Config) extends Actor with ActorLogging {

  import MainActor._

  implicit val timeout = Timeout(5.seconds)

  var nodesActor = Option.empty[ActorRef]
  var cloudActor = Option.empty[ActorRef]
  var httpActor  = Option.empty[ActorRef]

  def initDb(url: String): Try[ModelRegistry] = {
    val db = ModelRegistry(url)
    db.map { r =>
      r.up()
      r
    }
  }

  def initCloud(): Try[AbstractCloud] = {
    val cloud = new DigitalOceanCloud(
      cfg.getString("cloud.digitalocean.token"),
      cfg.getString("cloud.digitalocean.region"),
      cfg.getInt("cloud.digitalocean.imageId"),
      cfg.getInt("cloud.digitalocean.keyId"),
      cfg.getString("cloud.digitalocean.size"),
      ""
    )
    Success(cloud)
  }

  def startCloudActor(cloud: AbstractCloud): Try[ActorRef] = {
    val cloudActor = context.actorOf(CloudActor.props(cloud), "cloud")
    val fu = cloudActor ? CloudActor.Command.Start
    Try {
      Await.result(fu, timeout.duration).asInstanceOf[CloudActor.StartReply] match {
        case CloudActor.StartSuccess    => cloudActor
        case CloudActor.StartFailure(e) => throw new RuntimeException(e)
      }
    }
  }

  def startHttpActor(): Try[ActorRef] = {
    val httpActor = context.actorOf(HttpHandler.props, "http")
    val fu = IO(Http)(context.system) ? Http.Bind(httpActor, interface = "localhost", port = 3000)
    Try {
      Await.result(fu, timeout.duration) match {
        case e =>
          println(e.getClass)
          println(e.toString)
          httpActor
      }
    }
  }

  def startNodesActor(db: ModelRegistry, cloudActor: ActorRef): Try[ActorRef] = {
    val nodesActor = context.actorOf(NodesActor.props(db.nodes, cloudActor))
    val fu = nodesActor ? NodesActor.Command.Start
    Try {
      Await.result(fu, timeout.duration).asInstanceOf[NodesActor.StartReply] match {
        case NodesActor.StartSuccess    => nodesActor
        case NodesActor.StartFailure(e) => throw new RuntimeException(e)
      }
    }
  }

  def receive = {
    case Command.Start =>
      val re =
        for {
          db         <- initDb(cfg.getString("db.url"))
          cloud      <- initCloud()
          cloudActor <- startCloudActor(cloud)
          httpActor  <- startHttpActor()
          nodesActor <- startNodesActor(db, cloudActor)
        } yield true

    re match {
      case Success(_) => sender() ! StartSuccess
      case Failure(e) => sender() ! StartFailure(e.toString)
    }
  }
}

