package io.vexor.dd.actors

import java.util.UUID

import akka.actor.{Actor, ActorLogging, Props}
import io.vexor.dd.models.Server

class AttachCloudInstance extends Actor with ActorLogging {

  import AttachCloudInstance._

  def receive = {
    case s: Server.Persisted =>
      log.info(s"[role=${s.role}] Spawned")
      sender() ! Created(s.id, s.role)
    case unknown =>
      unhandled(unknown)
  }
}

object AttachCloudInstance {
  val props = Props[AttachCloudInstance]
  case class Created(id: UUID, role: String)
}
