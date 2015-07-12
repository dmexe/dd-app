package io.vexor.dd.actors

import java.util.UUID

import akka.actor.{Props, ActorLogging, Actor}
import io.vexor.dd.models.{DB, NodesTable}
import io.vexor.dd.cloud.AbstractCloud
import io.vexor.dd.Utils.OptionToTry

import scala.util.{Failure, Success, Try}

class AttachNodeActor(db: NodesTable, cloud: AbstractCloud) extends Actor with ActorLogging {

  import AttachNodeActor._

  def create(userId: UUID, role: String) = {
    val s = NodesTable.New(userId, role)
    db.save(s)
  }

  def find(userId: UUID, role: String) = {
    db.last(userId, role)
  }

  def attach(userId: UUID, role: String): AttachResult = {
    val re : Try[NodesTable.Persisted] =
      for {
        nodeRecord   <- find(userId, role) orElse create(userId, role) toTry(new NotFoundError(userId, role))
        nodeInstance <- cloud.create(role)
      } yield nodeRecord

    re match {
      case Success(node) => Attached(node)
      case Failure(e)    => AttachFailed(e)
    }
  }

  def receive = {
    case Attach(userId, role) =>
      sender() ! attach(userId, role)
  }
}

object AttachNodeActor {
  def props(db: DB.Session, cloud: AbstractCloud) : Props =
    Props(new AttachNodeActor(new NodesTable(db), cloud))

  class NotFoundError(userId: UUID, role: String)
    extends RuntimeException(s"Cannot found node with user_id=${userId} and role=${role}")

  case class Attach(userId: UUID, role: String)
  sealed trait AttachResult
  case class Attached(node: NodesTable.Persisted) extends AttachResult
  case class AttachFailed(e: Throwable) extends AttachResult
}
