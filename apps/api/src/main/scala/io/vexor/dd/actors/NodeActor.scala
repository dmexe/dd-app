package io.vexor.dd.actors

import akka.actor.FSM
import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import io.vexor.dd.cloud.AbstractCloud
import io.vexor.dd.models.NodesTable
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Try, Success}

class NodeActor(db: NodesTable, cloudActor: ActorRef) extends FSM[NodeActor.State, NodeActor.Data] with ActorLogging {

  import NodeActor._

  implicit val timeout = Timeout(5.seconds)

  val tickInterval = 5.seconds

  startWith(State.Idle, Data.Empty)

  when(State.Idle) {
    createNewNode orElse recoveryNode
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

  when(State.Shutdown) {
    handleShutdown
  }

  whenUnhandled {
    case Event(Command.Create(newNode), Data.Node(node)) =>
      stay() replying Reply.CreateSuccess(node)
    case Event(Command.Create(newNode), _) =>
      stay() replying Reply.CreateFailure(new RuntimeException(s"Cannot create a new node in $stateName state"))
    case Event(Command.Get, Data.Node(node)) =>
      stay() replying Reply.GetSuccess(node)
    case Event(Command.Get, _) =>
      stay() replying Reply.GetFailure(new RuntimeException(s"Cannot get a node in $stateName state"))
    case Event(Command.Status, data) =>
      stay() replying Reply.StatusSuccess(stateName, data)
  }

  onTransition {
    case State.Idle -> State.New =>
      self ! Command.CreateInstance

    case State.New -> State.Pending =>
      persistNode(self, stateData, nextStateData)

    case State.Pending -> State.Active =>
      persistNode(self, stateData, nextStateData)

    case _ -> State.Shutdown  =>
      persistAndShutdownNode(self, stateData, nextStateData)
  }

  onTransition {
    case _ -> State.Pending =>
      setTimer("awaitInstanceIsRunning", Command.AwaitInstanceIsRunning, tickInterval, repeat = true)
    case State.Pending -> _ =>
      cancelTimer("awaitInstanceIsRunning")
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

  initialize()

  //
  // Actions
  //

  // Idle
  def createNewNode: StateFunction = {
    case Event(Command.Create(newNode), Data.Empty) =>
      db.save(newNode) match {
        case Some(node) =>
          goto(State.New) using Data.Node(node) replying Reply.CreateSuccess(node)
        case None =>
          val msg = s"Cannot found saved node $newNode"
          goto(State.Idle) using Data.Error(msg) replying Reply.CreateFailure(runtimeException(msg))
      }
  }

  def recoveryNode: StateFunction = {
    case Event(Command.Recovery(node), _) =>
      log.info(s"Received: Command.Recovery($node)")
      node.status match {
        case NodeStatus.New =>
          goto(State.New) using Data.Node(node) replying Reply.RecoverySuccess(State.New)
        case NodeStatus.Pending =>
          goto(State.Pending) using Data.Node(node) replying Reply.RecoverySuccess(State.Pending)
        case NodeStatus.Active =>
          goto(State.Active) using Data.Node(node) replying Reply.RecoverySuccess(State.Active)
        case unknown =>
          val msg = s"Don't known how to recovery from $unknown"
          goto(State.Idle) using Data.Error(msg) replying Reply.RecoveryFailure(runtimeException(msg), node)
      }
  }

  // New
  def createInstanceForNode: StateFunction = {
    case Event(Command.CreateInstance, Data.Node(node)) =>
      val fu = cloudActor ? CloudCommand.Create(node.userId, node.role)
      val re = Try { Await.result(fu, timeout.duration).asInstanceOf[CloudReply.CreateResult] }
      re match {
        case Success(CloudReply.CreateSuccess(instance)) =>
          val newNode = node.copy(status = NodeStatus.Pending, cloudId = Some(instance.id))
          goto(State.Pending) using Data.Node(newNode)
        case Success(error) =>
          gotoShutdown(error.toString)
        case Failure(error) =>
          gotoShutdown(error.toString)
      }
  }

  // Pending
  def awaitInstanceIsRunning: StateFunction = {
    case Event(Command.AwaitInstanceIsRunning, Data.Node(node)) =>
      getInstance(node) match {
        case Success(CloudReply.GetSuccess(instance)) if instance.status == CloudStatus.On =>
          val activeNode = node.copy(status = NodeStatus.Active)
          goto(State.Active) using Data.Node(activeNode)
        case Success(CloudReply.GetSuccess(instance)) if instance.status == CloudStatus.Pending =>
          stay()
        case Success(error) =>
          gotoShutdown(error.toString)
        case Failure(error) =>
          gotoShutdown(error.toString)
      }
  }

  // Active
  def awaitInstanceTermination: StateFunction = {
    case Event(Command.AwaitInstanceTermination, Data.Node(node)) =>
      getInstance(node) match {
        case Success(CloudReply.GetSuccess(instance)) if instance.status == CloudStatus.On =>
          stay()
        case Success(_) =>
          gotoShutdown()
        case error =>
          gotoShutdown(error.toString)
      }
  }

  // Shutdown
  def handleShutdown: StateFunction = {
    case Event(Command.Shutdown, _) =>
      goto(State.Idle) using Data.Empty
  }

  // Any
  def handlePersisted: StateFunction = {
    case Event(Command.Persisted(newNode), Data.Node(_)) =>
      stay() using Data.Node(newNode)
  }

  // Unhandled
  def replyStatus: StateFunction = {
    case Event(Command.Status, data) =>
      stay() replying Reply.StatusSuccess(stateName, data)
  }

  //
  // Helpers
  //

  def getInstance(node: PersistedNode): Try[CloudReply.GetResult] = {
    val id = node.cloudId.getOrElse("")
    val fu = cloudActor ? CloudCommand.Get(id)
    Try { Await.result(fu, timeout.duration).asInstanceOf[CloudReply.GetResult] }
  }

  def runtimeException(m: String) = {
    new RuntimeException(m)
  }

  def gotoShutdown() = {
    goto(State.Shutdown) using Data.Empty
  }

  def gotoShutdown(error: String) = {
    goto(State.Shutdown) using Data.Error(error)
  }

  def persistNode(actor: ActorRef, oldData: Data, newData: Data): Unit = {
    (oldData, newData) match {
      case (Data.Node(oldNode), Data.Node(newNode)) =>
        db.save(
          oldNode,
          status  = newNode.status,
          cloudId = newNode.cloudId
        ) foreach { node => actor ! Command.Persisted(node) }
      case _ =>
    }
  }

  def persistAndShutdownNode(actor: ActorRef, oldData: Data, newData: Data): Unit = {
    (oldData, newData) match {
      case (Data.Node(oldNode), Data.Empty) =>
        db.save(oldNode, status = NodeStatus.Finished) foreach( node => actor ! Command.Shutdown)
      case (Data.Node(oldNode), Data.Error(e)) =>
        db.save(oldNode, status = NodeStatus.Broken) foreach( node => actor ! Command.Shutdown)
      case _ => actor ! Command.Shutdown
    }
  }
}

object NodeActor {

  type NewNode       = NodesTable.New
  type PersistedNode = NodesTable.Persisted
  val  NodeStatus    = NodesTable.Status

  val  CloudStatus   = AbstractCloud.Status
  val  CloudCommand  = CloudActor.Command
  val  CloudReply    = CloudActor.Reply

  object Command {
    case class  Create(node: NewNode)
    case object Get
    case object CreateInstance
    case object AwaitInstanceIsRunning
    case object AwaitInstanceTermination
    case class  Persisted(node: PersistedNode)
    case object Shutdown
    case object Status
    case class  Recovery(node: PersistedNode)
  }

  sealed trait State
  object State {
    case object Idle       extends State
    case object New        extends State
    case object Pending    extends State
    case object Active     extends State
    case object Shutdown   extends State
  }

  sealed trait Data
  object Data {
    case object Empty                     extends Data
    case class  Node(node: PersistedNode) extends Data
    case class  Error(e: String)          extends Data
  }

  object Reply {
    sealed trait CreateResult
    case class CreateSuccess(node: PersistedNode) extends CreateResult
    case class CreateFailure(e: Throwable) extends CreateResult

    sealed trait StatusResult
    case class StatusSuccess(state: State, data: Data) extends StatusResult

    sealed trait RecoveryResult
    case class RecoverySuccess(state: State) extends RecoveryResult
    case class RecoveryFailure(e: Throwable, node: PersistedNode) extends RecoveryResult

    sealed trait GetResult
    case class GetSuccess(node: PersistedNode) extends GetResult
    case class GetFailure(e: Throwable) extends GetResult
  }

  def props(db: NodesTable, cloud: ActorRef): Props = Props(new NodeActor(db, cloud))
}
