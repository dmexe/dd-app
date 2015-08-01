package io.vexor.docker.api.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.IO
import akka.pattern.ask
import com.typesafe.config.Config
import io.vexor.docker.api.DefaultTimeout
import io.vexor.docker.api.cloud.{AbstractCloud, CloudInit, DigitalOceanCloud}
import io.vexor.docker.api.handlers.HttpHandler
import io.vexor.docker.api.models.{CA, ModelRegistry, SshKey}
import spray.can.Http

import scala.concurrent.Await
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

class MainActor(cfg: Config) extends Actor with ActorLogging with DefaultTimeout {

  import MainActor._

  def initDb(url: String): Try[ModelRegistry] = {
    val db = ModelRegistry(url)
    db.map { r =>
      r.up()
      r
    }
  }

  def initDockerCa(reg: ModelRegistry): Try[CA] = {
    Try{ CA("docker", "Docker CA Authority", reg.properties) }
  }

  def initClientsCa(reg: ModelRegistry): Try[CA] = {
    Try{ CA("clients", "Proxy Clients CA Authority", reg.properties) }
  }

  def initCloudInit(ca: CA): Try[CloudInit] = {
    CloudInit.docker(ca)
  }

  def initCloud(cloudInit: CloudInit, sshKey: SshKey): Try[AbstractCloud] = {
    val cloud = new DigitalOceanCloud(
      cfg.getString("cloud.digitalocean.token"),
      cfg.getString("cloud.digitalocean.region"),
      cfg.getString("cloud.digitalocean.size"),
      cloudInit,
      sshKey
    )
    Success(cloud)
  }

  def initSshKey(reg: ModelRegistry): Try[SshKey] = {
    SshKey(reg.properties, "docker")
  }

  def startDockerActor(dockerCa: CA, clientsCa: CA): Try[ActorRef] = {
    val dockerActor = context.actorOf(DockerActor.props(dockerCa, clientsCa), "docker")
    Try(dockerActor)
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
    val nodesActor = context.actorOf(NodesActor.props(db.nodes, cloudActor), "nodes")
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
      val re: Try[Boolean] =
        for {
          db          <- initDb(cfg.getString("db.url"))
          dockerCa    <- initDockerCa(db)
          clientsCa   <- initClientsCa(db)
          cloudInit   <- initCloudInit(dockerCa)
          sshKey      <- initSshKey(db)
          cloud       <- initCloud(cloudInit, sshKey)
          dockerActor <- startDockerActor(dockerCa, clientsCa)
          cloudActor  <- startCloudActor(cloud)
          httpActor   <- startHttpActor()
          nodesActor  <- startNodesActor(db, cloudActor)
        } yield true

    re match {
      case Success(_) => sender() ! StartSuccess
      case Failure(e) => sender() ! StartFailure(e.toString)
    }
  }
}

