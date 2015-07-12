package io.vexor.dd.actors

import java.util.UUID

import akka.actor.{Props, ActorLogging, Actor}
import io.vexor.dd.models.NodesTable
import io.vexor.dd.models.NodesTable.Status
import io.vexor.dd.Utils.OptionToTry

import scala.util.{Failure, Success, Try}

class NodesActor(db: NodesTable) extends Actor with ActorLogging {

  import NodesActor._

  def createNewNode(userId: UUID, role: String) = {
    val s = NodesTable.New(userId, role)
    db.save(s)
  }

  def findLastNode(userId: UUID, role: String) = {
    db.last(userId, role)
  }

  def nodeNotFoundError(userId: UUID, role: String) = {
    new NodeNotFoundError(userId, role)
  }

  def nodeUpdateError(userId: UUID, role: String) = {
    new NodeUpdateError(userId, role)
  }

  def maybeUpdateNewNodeStatus(prev: NodesTable.Persisted): Try[NodesTable.Persisted] = {
    val newStatus =
      prev.status match {
        case Status.Frozen   => Status.New
        case Status.Broken   => Status.New
        case Status.Finished => Status.New
        case _               => prev.status
      }
    if(newStatus == prev.status) {
      Success(prev)
    } else {
      db.save(prev, status = newStatus).toTry(nodeUpdateError(prev.userId, prev.role))
    }
  }

  def upNode(userId: UUID, role: String): UpResult = {
    val re : Try[NodesTable.Persisted] =
      for {
        parentRecord <- findLastNode(userId, role) orElse createNewNode(userId, role) toTry nodeNotFoundError(userId, role)
        newRecord    <- maybeUpdateNewNodeStatus(parentRecord)
      } yield newRecord

    re match {
      case Success(node) => UpSuccess(node)
      case Failure(e)    => UpFailure(e)
    }
  }

  def receive = {
    case Up(userId, role) =>
      sender() ! upNode(userId, role)
  }
}

object NodesActor {

  def props(db: NodesTable) : Props = Props(new NodesActor(db))

  class NodeNotFoundError(userId: UUID, role: String)
    extends RuntimeException(s"Cannot found node with user_id=$userId and role=$role")
  class NodeUpdateError(userId: UUID, role: String)
    extends RuntimeException(s"Cannot update node with user_id=$userId and role=$role")

  case class Up(userId: UUID, role: String)
  sealed trait UpResult
  case class UpSuccess(node: NodesTable.Persisted) extends UpResult
  case class UpFailure(e: Throwable) extends UpResult
}
