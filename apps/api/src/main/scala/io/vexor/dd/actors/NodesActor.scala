package io.vexor.dd.actors

import akka.actor.{Actor, ActorLogging, Props}
import io.vexor.dd.models.{DB, NodesTable}

class NodesActor(db: NodesTable) extends Actor with ActorLogging {

  import NodesActor._

  def create(role: String) = {
    val s = NodesTable.New(role)
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
    case Up(role) =>
      val re : UpResult = find(role) orElse create(role) match {
        case Some(i) => Found(i)
        case None    => NotFound(role)
      }
      sender() ! re
  }
}

object NodesActor {

  case class Up(role: String)
  sealed trait UpResult
  case class Found(instance: NodesTable.Persisted) extends UpResult
  case class NotFound(role: String) extends UpResult

  def props(s: DB.Session) : Props =  Props(new NodesActor(NodesTable(s)))
}

