package io.vexor.dd.actors

import akka.actor.FSM.NullFunction
import akka.pattern.ask
import akka.actor.{ActorRef, FSM, Props, ActorLogging}
import io.vexor.dd.models.NodesTable
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await,Future}
import scala.util.Try

class NodesActor(db: NodesTable, cloud: ActorRef) extends FSM[NodesActor.State, NodesActor.Data] with ActorLogging {

  import NodesActor._
  import context.dispatcher

  startWith(State.Idle, Data.Empty)

  when(State.Idle) {
    awaitStart
  }

  when(State.Active) {
    NullFunction
  }

  onTransition {
    case a -> b =>
      log.info(s"Transition $a -> $b using $nextStateData")
  }

  def awaitStart: StateFunction = {
    case Event(Command.Start, _) =>
      recoveryNodes()
      goto(State.Active)
  }

  def recoveryNodes(): Unit = {
    val timeout = 10.seconds
    val nodes   = db.allRunning()
    val futures =
      Future.sequence(
        nodes.map { node =>
          val nodeActor = getNodeActor(node)
          ask(nodeActor, NodeActor.Command.Recovery(node))(timeout).mapTo[NodeActor.Reply.RecoveryResult]
        }
      )
    val re = Try{ Await.result(futures, timeout) }
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
    case object Empty                  extends Data
    case class Nodes(nodes: NodesList) extends Data
  }

  object Command {
    case object Start
    case object Recovered
  }

  def props(db: NodesTable, cloud: ActorRef) : Props = Props(new NodesActor(db, cloud))
}
