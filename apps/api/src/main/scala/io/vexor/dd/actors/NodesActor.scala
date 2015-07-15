package io.vexor.dd.actors

import akka.actor.FSM.NullFunction
import akka.actor.{FSM, Props, ActorLogging}
import io.vexor.dd.models.NodesTable

class NodesActor(db: NodesTable) extends FSM[NodesActor.State, NodesActor.Data] with ActorLogging {

  import NodesActor._

  startWith(State.Idle, Data.Empty)

  when(State.Idle) {
    awaitStart
  }

  when(State.Recovery) {
    awaitRecovered
  }

  when(State.Active) {
    NullFunction
  }

  onTransition {
    case State.Idle -> State.Recovery => {
      recoveryNodes(nextStateData)
      self ! Command.Recovered
    }
  }

  onTransition {
    case a -> b =>
      log.info(s"Transition $a -> $b using $nextStateData")
  }

  //
  //
  //

  def awaitStart: StateFunction = {
    case Event(Command.Start, _) =>
      val nodes = nodesNeedToRecovery()
      if(nodes.isEmpty) {
        goto(State.Active)
      } else {
        goto(State.Recovery) using Data.Nodes(nodes)
      }
  }

  def awaitRecovered: StateFunction = {
    case Event(Command.Recovered, _) =>
      goto(State.Active) using Data.Empty
  }

  //
  //
  //
  def nodesNeedToRecovery(): NodesList = {
    List.empty
  }

  def recoveryNodes(data: Data): Unit = {
    data match {
      case Data.Nodes(nodes) =>
      case _ =>
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

  def props(db: NodesTable) : Props = Props(new NodesActor(db))
}
