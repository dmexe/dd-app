package io.vexor.cloud.actors

import java.util.UUID

import akka.actor.FSM.NullFunction
import akka.pattern.ask
import akka.actor.{ActorRef, FSM, Props, ActorLogging}
import io.vexor.cloud.models.NodesTable
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await,Future}
import scala.util.{Try,Success,Failure}

class NodesActor(db: NodesTable, cloud: ActorRef) extends FSM[NodesActor.State, NodesActor.Data] with ActorLogging {

  import NodesActor._
  import context.dispatcher

  startWith(State.Idle, Data.Empty)

  when(State.Idle) {
    awaitStart
  }

  when(State.Active) {
    awaitNodeActions
  }

  onTransition {
    case a -> b =>
      log.info(s"Transition $a -> $b using $nextStateData")
  }

  def awaitStart: StateFunction = {
    case Event(Command.Start, _) =>
      val timeout = 10.seconds
      val nodes   = db.allRunning()

      log.info(s"Found ${nodes.size} nodes need to be recovered")
      val futures =
        Future.sequence(
          nodes.map { node =>
            val nodeActor = getNodeActor(node.userId, node.role)
            ask(nodeActor, NodeActor.Command.Recovery(node))(timeout).mapTo[NodeActor.Reply.RecoveryResult]
          }
        )

      val re = Try{ Await.result(futures, timeout) }
      re match {
        case Success(results: List[NodeActor.Reply.RecoveryResult]) =>
          processRecoveredNodeResult(results)
        case error =>
          goto(State.Idle) using Data.Error(error.toString) replying Reply.StartFailure(error.toString)
      }
  }

  def awaitNodeActions: StateFunction = {
    case Event(Command.Create(userId, role), _) =>
      val actor   = getNodeActor(userId, role)
      val newNode = NodesTable.New(userId, role)
      actor forward NodeActor.Command.Create(newNode)
      stay()
    case Event(Command.Get(userId, role), _) =>
      val actor   = getNodeActor(userId, role)
      actor forward NodeActor.Command.Get
      stay()
  }

  //
  //
  //

  def processRecoveredNodeResult(results: List[NodeActor.Reply.RecoveryResult]): State = {
    if(results.nonEmpty) {
      results.foreach {
        case NodeActor.Reply.RecoveryFailure(e, node) =>
          log.error(s"Recovery failure: $e [node=$node]")
        case _ =>
      }
      log.info(s"Successfuly recovered ${results.size} nodes")
    }
    goto(State.Active) using Data.Empty replying Reply.StartSuccess
  }

  def getNodeActor(userId: UUID, role: String): ActorRef = {
    val name = s"node-$userId-$role"
    context.child(name) getOrElse {
      context.actorOf(NodeActor.props(db, cloud))
    }
  }
}

object NodesActor {

  type NewNode       = NodesTable.New
  type PersistedNode = NodesTable.Persisted
  type NodesList     = List[PersistedNode]

  sealed trait State
  object State {
    case object Idle     extends State
    case object Active   extends State
    case object Recovery extends State
  }

  sealed trait Data
  object Data {
    case object Empty                   extends Data
    case class  Error(e: String)        extends Data
    case class  Nodes(nodes: NodesList) extends Data
  }

  object Command {
    case object Start
    case object Recovered
    case class  Create(userId: UUID, role: String)
    case class  Get(userId: UUID, role: String)
  }

  object Reply {
    sealed trait StartResult
    case object StartSuccess extends StartResult
    case class StartFailure(e: String) extends StartResult

    sealed trait CreateResult
    case class CreateSuccess(node: PersistedNode) extends CreateResult
    case class CreateFailure(e: String) extends CreateResult
  }

  def props(db: NodesTable, cloud: ActorRef) : Props = Props(new NodesActor(db, cloud))
}
