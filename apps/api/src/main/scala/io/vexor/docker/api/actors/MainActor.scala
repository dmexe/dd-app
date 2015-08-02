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
    Try { ModelRegistry(url).up() }
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
    Try{ SshKey(reg.properties, "docker") }
  }

  def startProxyActor(dockerCa: CA, clientsCa: CA, nodesActor: ActorRef): Try[ActorRef] = {
    Try{ context.actorOf(ProxyActor.props(dockerCa, clientsCa, nodesActor), "proxy") }
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

  def startCertsActor(clientsCa: CA, reg: ModelRegistry): Try[ActorRef] = {
    Try{ context.actorOf(CertsActor.props(reg.certs, clientsCa), "certs") }
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
          cloudActor  <- startCloudActor(cloud)
          certsActor  <- startCertsActor(clientsCa, db)
          httpActor   <- startHttpActor()
          nodesActor  <- startNodesActor(db, cloudActor)
          proxyActor  <- startProxyActor(dockerCa, clientsCa, nodesActor)
        } yield true

    re match {
      case Success(_) => sender() ! StartSuccess
      case Failure(e) => sender() ! StartFailure(e.toString)
    }
  }
}

