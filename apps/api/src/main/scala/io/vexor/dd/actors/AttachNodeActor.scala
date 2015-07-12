package io.vexor.dd.actors

import akka.actor.{Props, ActorLogging, Actor}
import io.vexor.dd.models.{DB, NodesTable}
import io.vexor.dd.cloud.AbstractCloud
import io.vexor.dd.Utils.OptionToTry

import scala.util.{Failure, Success, Try}

class AttachNodeActor(db: NodesTable, cloud: AbstractCloud) extends Actor with ActorLogging {

  import AttachNodeActor._

  def create(role: String) = {
    val s = NodesTable.New(role)
    db.save(s)
    db.oneByRole(role)
  }

  def find(role: String) = {
    db.oneByRole(role)
  }

  def attach(role: String): AttachResult = {
    val re : Try[NodesTable.Persisted] =
      for {
        nodeRecord   <- find(role) orElse create(role) toTry(new NotFoundError(role))
        nodeInstance <- cloud.create(role)
      } yield nodeRecord

    re match {
      case Success(node) => Attached(node)
      case Failure(e)    => AttachFailed(e)
    }
  }

  def receive = {
    case Attach(role) =>
      sender() ! attach(role)
  }
}

object AttachNodeActor {
  def props(db: DB.Session, cloud: AbstractCloud) : Props =
    Props(new AttachNodeActor(new NodesTable(db), cloud))

  class NotFoundError(role: String) extends Exception(s"Cannot found node with role=${role}")

  case class Attach(role: String)
  sealed trait AttachResult
  case class Attached(node: NodesTable.Persisted) extends AttachResult
  case class AttachFailed(e: Throwable) extends AttachResult
}
