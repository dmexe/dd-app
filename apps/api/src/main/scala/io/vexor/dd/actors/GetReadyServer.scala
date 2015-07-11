package io.vexor.dd.actors

import akka.actor.{Actor, ActorLogging, Props}
import io.vexor.dd.models.Server
import io.vexor.dd.models.Server.Persisted

object GetReadyServer {
  def props(s: DB.Session) : Props =  Props(new GetReadyServer(Server(s)))
}

class GetReadyServer(db: Server) extends Actor with ActorLogging {

  def create(role: String) = {
    val s = Server.New(role)
    db.save(s)
    db.oneByRole(role) map { v =>
      log.info(s"Created role=$role server=${v.id} status=${v.status}") ; v
    }
  }

  def find(role: String) = {
    db.oneByRole(role) map { v =>
      log.info(s"Found role=$role server=${v.id} status=${v.status}") ; v
    }
  }

  def receive = {
    case role : String =>
      log.info(s"Get ready server for role=$role")
      val re : Option[Persisted] = find(role) orElse create(role)
      sender() ! re
  }
}
