package io.vexor.docker.api.actors

import java.util.UUID

import akka.pattern.ask
import akka.actor.{ActorRef, FSM, Props, ActorLogging}
import io.vexor.docker.api.models.NodesTable
import io.vexor.docker.api.models.NodesTable.New
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await,Future}
import scala.util.{Try,Success}

class NodesActor(db: NodesTable, cloud: ActorRef) extends FSM[NodesActor.State, NodesActor.Data] with ActorLogging {

  import NodesActor._
  import context.dispatcher

  val cleanupInterval = 1.minute

  startWith(State.Idle, Data.Empty)

  when(State.Idle) {
    awaitStart
  }

  when(State.Active) {
    awaitNodeActions
  }

  onTransition {
    case _ -> State.Active =>
      setTimer("tick", Command.Tick, cleanupInterval, repeat = true)
    case State.Active -> _ =>
      cancelTimer("tick")
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
            ask(nodeActor, NodeActor.Command.Recovery(node))(timeout).mapTo[NodeActor.RecoveryReply]
          }
        )

      val re = Try{ Await.result(futures, timeout) }
      re match {
        case Success(results: List[NodeActor.RecoveryReply]) =>
          processRecoveredNodeResult(results)
        case error =>
          goto(State.Idle) using Data.Error(error.toString) replying StartFailure(error.toString)
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

    case Event(Command.GetInstance(userId, role), _) =>
      val actor   = getNodeActor(userId, role)
      actor forward NodeActor.Command.GetInstance
      stay()

    case Event(Command.Tick, _) =>
      val cloudIds = db.allRunning() flatMap(_.cloudId)
      ask(cloud, CloudActor.Command.Cleanup(cloudIds))(5.seconds)
      stay()
  }

  //
  //
  //

  def processRecoveredNodeResult(results: List[NodeActor.RecoveryReply]): State = {
    if(results.nonEmpty) {
      results.foreach {
        case NodeActor.RecoveryFailure(e, node) =>
          log.error(s"Recovery failure: $e [node=$node]")
        case _ =>
      }
      log.info(s"Successfuly recovered ${results.size} nodes")
    }
    goto(State.Active) using Data.Empty replying StartSuccess
  }

  def getNodeActor(userId: UUID, role: String): ActorRef = {
    val name = s"node-$userId-$role"
    context.child(name) getOrElse {
      context.actorOf(NodeActor.props(db, cloud), name)
    }
  }
}

object NodesActor {

  type NewNode       = New
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
    case class  GetInstance(userId: UUID, role: String)
    case object Tick
  }

  sealed trait StartReply
  case object StartSuccess extends StartReply
  case class StartFailure(e: String) extends StartReply

  sealed trait CreateReply
  case class CreateSuccess(node: PersistedNode) extends CreateReply
  case class CreateFailure(e: String) extends CreateReply

  def props(db: NodesTable, cloud: ActorRef) : Props = Props(new NodesActor(db, cloud))
}
