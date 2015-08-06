package io.vexor.docker.api.actors

import java.util.UUID

import akka.actor.FSM
import akka.actor._
import akka.pattern.ask
import io.vexor.docker.api.DefaultTimeout
import io.vexor.docker.api.cloud.AbstractCloud
import io.vexor.docker.api.models.NodesTable
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.{Try, Success, Failure}

class NodeActor(db: NodesTable, cloudActor: ActorRef, userId: UUID, role: String) extends FSM[NodeActor.State, NodeActor.Data] with ActorLogging
with DefaultTimeout {

  import NodeActor._

  lazy val tickInterval   = 5.seconds
  lazy val pendingTimeout = 5.minutes

  startWith(State.Recovery, Data.Empty)

  when(State.Recovery) {
    awaitRecovery
  }

  when(State.Idle) {
    awaitNodeCreation
  }

  when(State.New) {
    createInstanceForNode
  }

  when(State.Pending) {
    awaitInstanceIsRunning orElse handlePersisted
  }

  when(State.Active) {
    awaitInstanceTermination orElse handlePersisted
  }

  whenUnhandled {
    case Event(Command.Create, Data.Node(node)) =>
      stay() replying CreateSuccess(node)
    case Event(Command.Create, data) =>
      stay() replying CreateFailure(s"Cannot create a new node in a $stateName state with the data $data")

    case Event(Command.Get, Data.Node(node)) =>
      stay() replying GetSuccess(node)
    case Event(Command.Get, data) =>
      stay() replying GetFailure(s"Cannot get a node in a $stateName state with the $data")

    case Event(Command.GetInstance, Data.Node(node)) =>
      getInstance(node) match {
        case Success(CloudActor.GetSuccess(instance)) if instance.status == CloudStatus.On =>
          stay() replying GetInstanceSuccess(instance)
        case error =>
          stay() replying GetInstanceFailure(error.toString)
      }
    case Event(Command.GetInstance, data) =>
      stay() replying GetInstanceFailure(s"Cannot get a instance in a $stateName state with the $data")

    case Event(Command.Status, data) =>
      stay() replying StatusSuccess(stateName, data)
  }

  onTransition {
    case _ -> State.New =>
      self ! Command.CreateInstance

    case State.New -> State.Pending =>
      persistNode(self, stateData, nextStateData)

    case State.Pending -> State.Active =>
      persistNode(self, stateData, nextStateData)
  }

  onTransition {
    case _ -> State.Pending =>
      setTimer("awaitInstanceIsRunning", Command.AwaitInstanceIsRunning, tickInterval, repeat = true)
      setTimer("pendingTimeoutReached",  Command.PendingTimeoutReached, pendingTimeout, repeat = false)
    case State.Pending -> _ =>
      cancelTimer("awaitInstanceIsRunning")
      cancelTimer("pendingTimeoutReached")
  }

  onTransition {
    case _ -> State.Active =>
      setTimer("awaitInstanceTermination", Command.AwaitInstanceTermination, tickInterval, repeat = true)
    case State.Active -> _ =>
      cancelTimer("awaitInstanceTermination")
  }

  onTransition {
    case a -> b =>
      log.info(s"Transition $a -> $b using $nextStateData")
  }

  override def preStart(): Unit = {
    self ! Command.Recovery
  }

  initialize()

  //
  // Actions
  //

  def awaitRecovery(): StateFunction = {
    case Event(Command.Recovery, _) =>
      val maybeNode = db.one(userId, role)
      log.info(s"Recovery [node=$maybeNode]")
      maybeNode match {
        case Some(node) if node.status == NodeStatus.New =>
          goto(State.New) using Data.Node(node)
        case Some(node) if node.status == NodeStatus.Pending =>
          goto(State.Pending) using Data.Node(node)
        case Some(node) if node.status == NodeStatus.Active =>
          goto(State.Active) using Data.Node(node)
        case _ =>
          goto(State.Idle) using Data.Empty
      }
  }


  // Idle
  def awaitNodeCreation: StateFunction = {
    case Event(Command.Create, Data.Empty) =>
      val newNode = NodesTable.New(userId, role)
      val node = db.save(newNode)
      goto(State.New) using Data.Node(node) replying CreateSuccess(node)
  }

  // New
  def createInstanceForNode: StateFunction = {
    case Event(Command.CreateInstance, Data.Node(node)) =>
      val fu = cloudActor ? CloudCommand.Create(node.userId, node.role, node.version)
      val re = Try { Await.result(fu, timeout.duration).asInstanceOf[CloudActor.CreateReply] }
      re match {
        case Success(CloudActor.CreateSuccess(instance)) =>
          val newNode = node.copy(status = NodeStatus.Pending, cloudId = Some(instance.id))
          goto(State.Pending) using Data.Node(newNode)
        case error =>
          gotoShutdown(node, error.toString)
      }
  }

  // Pending
  def awaitInstanceIsRunning: StateFunction = {
    case Event(Command.AwaitInstanceIsRunning, Data.Node(node)) =>
      getInstance(node) match {
        case Success(CloudActor.GetSuccess(instance)) if instance.status == CloudStatus.On =>
          val activeNode = node.copy(status = NodeStatus.Active)
          goto(State.Active) using Data.Node(activeNode)
        case Success(CloudActor.GetSuccess(instance)) if Seq(CloudStatus.Off, CloudStatus.Broken).contains(instance.status) =>
          gotoShutdown(node, s"Instance in ${instance.status}")
        case _ =>
          stay()
      }
    case Event(Command.PendingTimeoutReached, Data.Node(node)) =>
      gotoShutdown(node, s"Pending timeout $pendingTimeout was reached")
  }

  // Active
  def awaitInstanceTermination: StateFunction = {
    case Event(Command.AwaitInstanceTermination, Data.Node(node)) =>
      getInstance(node) match {
        case Success(CloudActor.GetSuccess(instance)) if instance.status == CloudStatus.On =>
          stay()
        case error =>
          gotoShutdown(node, error.toString)
      }
  }

  // Any
  def handlePersisted: StateFunction = {
    case Event(Command.Persisted(newNode), Data.Node(_)) =>
      stay() using Data.Node(newNode)
  }

  //
  // Helpers
  //
  def getInstance(node: PersistedNode): Try[CloudActor.GetReply] = {
    val id = node.cloudId.getOrElse("")
    val fu = cloudActor ? CloudCommand.Get(id)
    Try { Await.result(fu, timeout.duration).asInstanceOf[CloudActor.GetReply] }
  }

  def gotoShutdown(node: PersistedNode, status: CloudStatus.Value): State = {
    log.info(s"Node successfuly finished [instance.status=$status]")
    db.save(node, status = NodeStatus.Finished)
    goto(State.Idle) using Data.Empty
  }

  def gotoShutdown(node: PersistedNode, error: String): State = {
    log.error(s"Node shutdown with error: $error [node=$node]")
    db.save(node, status = NodeStatus.Broken)
    goto(State.Idle) using Data.Empty
  }

  def persistNode(actor: ActorRef, oldData: Data, newData: Data): Unit = {
    (oldData, newData) match {
      case (Data.Node(oldNode), Data.Node(newNode)) =>
        val node = db.save(oldNode, status  = newNode.status, cloudId = newNode.cloudId)
        actor ! Command.Persisted(node)
      case _ =>
    }
  }
}

object NodeActor {

  type NewNode       = NodesTable.New
  type PersistedNode = NodesTable.Persisted
  val  NodeStatus    = NodesTable.Status

  val  CloudStatus   = AbstractCloud.Status
  val  CloudCommand  = CloudActor.Command

  object Command {
    case object Create
    case object Get
    case object GetInstance
    case object Status

    // private commands
    case object Recovery
    case object CreateInstance
    case object AwaitInstanceIsRunning
    case object PendingTimeoutReached
    case object AwaitInstanceTermination
    case class  Persisted(node: PersistedNode)
  }

  sealed trait State
  object State {
    case object Recovery extends State
    case object Idle     extends State
    case object New      extends State
    case object Pending  extends State
    case object Active   extends State
  }

  sealed trait Data
  object Data {
    case object Empty                     extends Data
    case class  Node(node: PersistedNode) extends Data
  }

  sealed trait CreateReply
  case class CreateSuccess(node: PersistedNode) extends CreateReply
  case class CreateFailure(e: String) extends CreateReply

  sealed trait StatusResult
  case class StatusSuccess(state: State, data: Data) extends StatusResult

  sealed trait GetReply
  case class GetSuccess(node: PersistedNode) extends GetReply
  case class GetFailure(e: String) extends GetReply

  sealed trait GetInstanceReply
  case class GetInstanceSuccess(instance: AbstractCloud.Instance) extends GetInstanceReply
  case class GetInstanceFailure(e: String) extends GetInstanceReply

  def props(db: NodesTable, cloud: ActorRef, userId: UUID, role: String): Props = Props(new NodeActor(db, cloud, userId, role))
}
