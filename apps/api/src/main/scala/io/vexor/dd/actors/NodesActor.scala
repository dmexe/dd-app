package io.vexor.dd.actors

import java.util.UUID

import akka.actor.{Props, ActorLogging, Actor}
import io.vexor.dd.models.NodesTable
import io.vexor.dd.models.NodesTable.Status
import io.vexor.dd.Utils.OptionToTry

import scala.util.{Failure, Success, Try}

class NodesActor(db: NodesTable) extends Actor with ActorLogging {

  import NodesActor._

  //
  // Helper methods
  //

  def createNewNode(userId: UUID, role: String) = {
    val s = NodesTable.New(userId, role)
    db.save(s)
  }

  def nodeNotFoundError(userId: UUID, role: String) = {
    new NodeNotFoundError(userId, role)
  }

  def nodeUpdateError(node: NodesTable.Persisted) = {
    new NodeUpdateError(node)
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
      db.save(prev, status = newStatus).toTry(nodeUpdateError(prev))
    }
  }

  //
  // Actions
  //

  def upNodeAction(userId: UUID, role: String): UpNodeResult = {
    val re : Try[NodesTable.Persisted] =
      for {
        parentRecord <- db.last(userId, role) orElse createNewNode(userId, role) toTry nodeNotFoundError(userId, role)
        newRecord    <- maybeUpdateNewNodeStatus(parentRecord)
      } yield newRecord

    re match {
      case Success(node) => UpNodeSuccess(node)
      case Failure(e)    => UpNodeFailure(e)
    }
  }

  def getNodeAction(userId: UUID, role: String): GetNodeResult = {
    val re = db.last(userId, role)
    re match {
      case Some(n) => GetNodeSuccess(n)
      case None    => GetNodeFailure(nodeNotFoundError(userId, role))
    }
  }

  def newNodesAction(): NewNodesResult = {
    val re = db.allNew()
    NewNodesSuccess(re)
  }

  def attachNodeAction(node: NodesTable.Persisted, cloudId: String): AttachNodeResult = {
    val re = db.save(node, status = Status.Active, cloudId = cloudId)
    re match {
      case Some(node) => AttachNodeSuccess(node)
      case None       => AttachNodeFailure(nodeUpdateError(node))
    }
  }

  def receive = {
    case UpNode(userId, role) =>
      sender() ! upNodeAction(userId, role)
    case GetNode(userId, role) =>
      sender() ! getNodeAction(userId, role)
    case NewNodes() =>
      sender() ! newNodesAction()
    case AttachNode(node, cloudId) =>
      sender() ! attachNodeAction(node, cloudId)
  }
}

object NodesActor {

  def props(db: NodesTable) : Props = Props(new NodesActor(db))

  class NodeNotFoundError(userId: UUID, role: String)
    extends RuntimeException(s"Cannot found node with user_id=$userId and role=$role")
  class NodeUpdateError(node: NodesTable.Persisted)
    extends RuntimeException(s"Cannot update node $node")

  case class UpNode(userId: UUID, role: String)
  sealed trait UpNodeResult
  case class UpNodeSuccess(node: NodesTable.Persisted) extends UpNodeResult
  case class UpNodeFailure(e: Throwable) extends UpNodeResult

  case class GetNode(userId: UUID, role: String)
  sealed trait GetNodeResult
  case class GetNodeSuccess(node: NodesTable.Persisted) extends GetNodeResult
  case class GetNodeFailure(e: Throwable) extends GetNodeResult

  case class NewNodes()
  sealed trait NewNodesResult
  case class NewNodesSuccess(nodes: List[NodesTable.Persisted]) extends NewNodesResult

  case class AttachNode(node: NodesTable.Persisted, cloudId: String)
  sealed trait AttachNodeResult
  case class AttachNodeSuccess(node: NodesTable.Persisted) extends AttachNodeResult
  case class AttachNodeFailure(e: Throwable) extends AttachNodeResult
}
