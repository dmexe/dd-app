package io.vexor.dd.actors

import akka.actor.FSM
import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import io.vexor.dd.cloud.AbstractCloud
import io.vexor.dd.models.NodesTable
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.{Try, Success}

class NodeActor(db: NodesTable, cloudActor: ActorRef) extends FSM[NodeActor.State, NodeActor.Data] with ActorLogging {

  import NodeActor._

  implicit val timeout = Timeout(10.seconds)

  startWith(State.Idle, Data.Empty)

  when(State.Idle) {
    createNewNode
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
    replyStatus
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
      setTimer("awaitInstanceIsRunning", Command.AwaitInstanceIsRunning, 5.seconds, repeat = true)
    case State.Pending -> _ =>
      cancelTimer("awaitInstanceIsRunning")
  }

  onTransition {
    case _ -> State.Active =>
      setTimer("awaitInstanceTermination", Command.AwaitInstanceTermination, 5.seconds, repeat = true)
    case State.Active -> _ =>
      cancelTimer("awaitInstanceTermination")
  }

  onTransition {
    case a -> b =>
      log.warning(s"\n\n!!!!!!!!!!!!!!!!!!!!\n $a -> $b using $nextStateData\n\n")
  }

  initialize()

  //
  // Actions
  //

  // Idle
  def createNewNode: StateFunction = {
    case Event(Command.Create(newNode), _) =>
      db.save(newNode) match {
        case Some(node) =>
          goto(State.New) using Data.Node(node) replying Reply.CreateSuccess(node)
        case None =>
          stay() replying Reply.CreateFailure(notFoundError(newNode))
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
        case error =>
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
        case error =>
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

  def notFoundError(newNode: NewNode) = {
    new RuntimeException(s"Cannot found node for $newNode")
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
  val  CloudReply    = CloudActor.Reply

  object Command {
    case class  Create(node: NewNode)
    case object CreateInstance
    case object AwaitInstanceIsRunning
    case object AwaitInstanceTermination
    case class  Persisted(node: PersistedNode)
    case object Shutdown
    case object Status
  }

  sealed trait State
  object State {
    case object Idle     extends State
    case object New      extends State
    case object Pending  extends State
    case object Active   extends State
    case object Shutdown extends State
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
    case class CreateFailure(e: Throwable)        extends CreateResult

    sealed trait StatusResult
    case class StatusSuccess(state: State, data: Data)
  }

  def props(db: NodesTable, cloud: ActorRef): Props = Props(new NodeActor(db, cloud))
}
