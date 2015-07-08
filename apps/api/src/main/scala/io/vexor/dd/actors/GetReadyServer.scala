package io.vexor.dd.actors

import akka.actor.{Actor, ActorLogging, Props}
import io.vexor.dd.models.Server

class GetReadyServer(db : Server) extends Actor with ActorLogging {

  import GetReadyServer._

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

  def notFound(role: String) = {
    Some(NotFound()) map { v =>
      log.warning(s"Not found role=$role") ; v
    }
  }

  def receive = {
    case role : String =>
      log.info(s"Get ready server for role=$role")
      val re = find(role) orElse create(role) orElse notFound(role)
      sender() ! re.get
  }
}

object GetReadyServer {
  case class NotFound()
  def props(db: Server) : Props = Props(new GetReadyServer(db))
}
