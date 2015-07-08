package io.vexor.dd.actors

import akka.actor.{Actor, ActorLogging, Props}
import io.vexor.dd.models.Server

class RequestNewServerByRole extends Actor with ActorLogging {

  import RequestNewServerByRole._

  def create(role: String) = {
    val newServer = Server.NewRecord(role)
    Server.save(newServer)
    Server.oneByRole(role) map { v =>
      log.info(s"[role=${role}] Created ${v}") ; v
    }
  }

  def find(role: String) = {
    Server.oneByRole(role) map { v =>
      log.info(s"[role=${role}] Found ${v}") ; v
    }
  }

  def notFound(role: String) = {
    Some(NotFound(role)) map { v =>
      log.warning(s"[role=${role}] not found") ; v
    }
  }

  def receive = {
    case role : String =>
      log.info(s"[role=${role}] Request for new server")
      val re = find(role) orElse(create(role)) orElse(notFound(role))
      sender() ! re.get
    case unknown =>
      unhandled(unknown)
  }
}

object RequestNewServerByRole {
  val props = Props[RequestNewServerByRole]
  case class NotFound(role: String)
}
