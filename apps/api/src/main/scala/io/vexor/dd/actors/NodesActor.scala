package io.vexor.dd.actors

import akka.actor.FSM.NullFunction
import akka.pattern.ask
import akka.actor.{ActorRef, FSM, Props, ActorLogging}
import io.vexor.dd.models.NodesTable
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await,Future}
import scala.util.{Try,Success,Failure}

class NodesActor(db: NodesTable, cloud: ActorRef) extends FSM[NodesActor.State, NodesActor.Data] with ActorLogging {

  import NodesActor._
  import context.dispatcher

  startWith(State.Idle, Data.Empty)

  when(State.Idle) {
    case Event(Command.Start, _) =>
      recoveryNodes()
  }

  when(State.Active) {
    NullFunction
  }

  onTransition {
    case a -> b =>
      log.info(s"Transition $a -> $b using $nextStateData")
  }

  def recoveryNodes(): State = {
    val timeout = 10.seconds
    val nodes   = db.allRunning()

    log.info(s"Found ${nodes.size} nodes need to be recovered")
    val futures =
      Future.sequence(
        nodes.map { node =>
          val nodeActor = getNodeActor(node)
          ask(nodeActor, NodeActor.Command.Recovery(node))(timeout).mapTo[NodeActor.Reply.RecoveryResult]
        }
      )
    val re = Try{ Await.result(futures, timeout) }

    re match {
      case Success(results: List[NodeActor.Reply.RecoveryResult]) =>
        processRecoveredNodeResult(results)
      case Failure(error: Throwable) =>
        goto(State.Idle) using Data.Error(error.toString) replying Reply.StartFailure(error)
      case error =>
        goto(State.Idle) using Data.Error(error.toString) replying Reply.StartFailure(new RuntimeException(error.toString))
    }
  }

  def processRecoveredNodeResult(results: List[NodeActor.Reply.RecoveryResult]): State = {
    if(results.nonEmpty) {
      results.foreach {
        case NodeActor.Reply.RecoveryFailure(e, node) =>
          log.error(s"Recovery failure: $e [node=$node]")
        case _ =>
          false
      }
      log.info(s"Successfuly recovered ${results.size} nodes")
    }
    goto(State.Active) using Data.Empty replying Reply.StartSuccess
  }

  def getNodeActor(node: PersistedNode): ActorRef = {
    val name = s"node-${node.userId}-${node.role}"
    context.child(name) getOrElse {
      context.actorOf(NodeActor.props(db, cloud))
    }
  }
}

object NodesActor {

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
  }

  object Reply {
    sealed trait StartResult
    case object StartSuccess extends StartResult
    case class StartFailure(e: Throwable) extends StartResult
  }

  def props(db: NodesTable, cloud: ActorRef) : Props = Props(new NodesActor(db, cloud))
}
