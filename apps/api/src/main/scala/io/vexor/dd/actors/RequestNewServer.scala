package io.vexor.dd.actors

import akka.actor.{Actor, ActorLogging, Props}
import io.vexor.dd.models.Server

class RequestNewServer extends Actor with ActorLogging {

  import RequestNewServer._

  def receive = {
    case Role(role) =>
      val newServer = Server.NewRecord(role)
      Server.Table.save(newServer)
      sender() ! Created(role)
    case unknown =>
      unhandled(unknown)
  }
}

object RequestNewServer {
  val props = Props[RequestNewServer]
  case class Role(role: String)
  case class Created(role: String)
  case class Unknown()
}
