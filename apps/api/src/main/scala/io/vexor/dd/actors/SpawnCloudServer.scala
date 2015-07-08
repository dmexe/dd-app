package io.vexor.dd.actors

import java.util.UUID
import akka.actor.{Actor, ActorLogging, Props}
import io.vexor.dd.models.Server

class SpawnCloudServer extends Actor with ActorLogging {

  import SpawnCloudServer._

  def receive = {
    case s: Server.PersistedRecord => {
      log.info(s"[role=${s.role}] Spawned")
      sender() ! Created(s.id, s.role)
    }
    case unknown => {
      unhandled(unknown)
    }
  }
}

object SpawnCloudServer {
  val props = Props[SpawnCloudServer]
  case class Created(id: UUID, role: String)
}
