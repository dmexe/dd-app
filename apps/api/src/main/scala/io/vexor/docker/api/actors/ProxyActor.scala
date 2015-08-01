package io.vexor.docker.api.actors

import java.util.UUID

import akka.actor.{ActorRef, Actor, ActorLogging, Props}
import akka.pattern.ask
import io.vexor.docker.api.DefaultTimeout
import io.vexor.docker.api.models.{KeyGen, CA}

import scala.concurrent.Await
import scala.util.{Try,Success}

class ProxyActor(dockerCa: CA, clientsCa: CA, nodesActor: ActorRef) extends Actor with ActorLogging with DefaultTimeout {

  import ProxyActor._

  def getCredentials(subject: String) = {
    val dockerRe   = KeyGen.toPEM(KeyGen.genCert(dockerCa.re, subject))
    val clientsRe  = KeyGen.toPEM(KeyGen.genCert(clientsCa.re, subject))
    val dockerTls  = TlsInfo(dockerCa.certPem, dockerRe.cert, dockerRe.privateKey)
    val clientsTls = TlsInfo(clientsCa.certPem, clientsRe.cert, clientsRe.privateKey)

    CredentialsSuccess(dockerTls, clientsTls)
  }

  def getInstanceIp(userId: UUID, role: String) = {
    val fu = nodesActor ? NodesActor.Command.Get(userId, role)
    val re = Try{ Await.result(fu, timeout.duration).asInstanceOf[NodeActor.GetReply] }
    re match {
      case Success(NodeActor.GetSuccess(node)) =>
      case e =>
    }
  }

  def receive = {
    case Command.Credentials(subject) =>
      sender() ! getCredentials(subject)
    case Command.GetInstanceIp(subject) =>

  }
}

object ProxyActor {

  object Command {
    case class Credentials(subject: String)
    case class GetInstanceIp(subject: String)
  }

  case class TlsInfo(ca: String, cert: String, key: String)

  sealed trait CredentialsReply
  case class CredentialsSuccess(docker: TlsInfo, clients: TlsInfo) extends CredentialsReply

  def props(dockerCa: CA, clientCa: CA, nodesActor: ActorRef): Props = Props(new ProxyActor(dockerCa, clientCa, nodesActor))
}
