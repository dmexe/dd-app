package io.vexor.docker.api.actors

import akka.actor.{Actor, ActorLogging, Props}
import io.vexor.docker.api.models.{KeyGen, CA}

class DockerActor(dockerCa: CA, clientsCa: CA) extends Actor with ActorLogging {

  import DockerActor._

  def getCredentials(subject: String) = {
    val dockerRe   = KeyGen.toPEM(KeyGen.genCert(dockerCa.re, subject))
    val clientsRe  = KeyGen.toPEM(KeyGen.genCert(clientsCa.re, subject))
    val dockerTls  = TlsInfo(dockerCa.certPem, dockerRe.cert, dockerRe.privateKey)
    val clientsTls = TlsInfo(clientsCa.certPem, clientsRe.cert, clientsRe.privateKey)

    CredentialsSuccess(dockerTls, clientsTls)
  }

  def receive = {
    case Command.Credentials(subject) =>
      sender() ! getCredentials(subject)
  }
}

object DockerActor {

  object Command {
    case class Credentials(subject: String)
  }

  case class TlsInfo(ca: String, cert: String, key: String)

  sealed trait CredentialsReply
  case class CredentialsSuccess(docker: TlsInfo, clients: TlsInfo) extends CredentialsReply

  def props(dockerCa: CA, clientCa: CA): Props = Props(new DockerActor(dockerCa, clientCa))
}
