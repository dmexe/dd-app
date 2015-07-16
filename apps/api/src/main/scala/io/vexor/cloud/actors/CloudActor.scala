package io.vexor.cloud.actors

import java.util.UUID

import akka.actor.{FSM, Props, ActorLogging}
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import io.vexor.cloud.cloud.AbstractCloud

import scala.util.Success

class CloudActor(cloud: AbstractCloud) extends FSM[CloudActor.State, CloudActor.Data] with ActorLogging {

  import context.dispatcher
  import CloudActor._

  val tickInterval   = 45.seconds
  val cleanupTimeout = 1.minute

  startWith(State.Idle, Data.Empty)

  when(State.Idle) {
    awaitStart
  }

  when(State.Active) {
    awaitCreate orElse awaitGetInstance orElse awaitCleanup orElse awaitTick
  }

  onTransition {
    case _ -> State.Active =>
      setTimer("tick", Command.Tick, tickInterval, repeat = true)
    case State.Active -> _ =>
      cancelTimer("tick")
  }

  onTransition {
    case a -> b =>
      log.info(s"Transition $a -> $b using $nextStateData")
  }

  def awaitStart: StateFunction = {
    case Event(Command.Start, _) =>
      cloud.all() match {
        case Success(instances: InstanceList) =>
          goto(State.Active) using Data.Instances(instances) replying Reply.StartSuccess
        case error =>
          goto(State.Idle) using Data.Error(error.toString) replying Reply.StartFailure(error.toString)
      }
  }

  def awaitCreate: StateFunction = {
    case Event(Command.Create(userId, role, version), Data.Instances(instances)) =>
      cloud.create(userId, role, version) match {
        case Success(instance: Instance) =>
          val newInstances = instances ++ List(instance)
          logInstances(newInstances)
          stay() using Data.Instances(newInstances) replying Reply.CreateSuccess(instance)
        case error =>
          stay() replying Reply.CreateFailure(error.toString)
      }
  }

  def awaitTick: StateFunction = {
    case Event(Command.Tick, Data.Instances(oldInstances)) =>
      val newInstances =
        cloud.all() match {
          case Success(i: InstanceList) => i
          case _ => oldInstances
        }
      if (newInstances != oldInstances) {
        logInstances(newInstances)
        stay() using Data.Instances(newInstances)
      } else {
        stay()
      }
  }

  def awaitGetInstance: StateFunction = {
    case Event(Command.Get(instanceId), Data.Instances(instances)) =>
      val re = instances
        .find      ( _.id == instanceId)
        .map       ( Reply.GetSuccess  )
        .getOrElse ( Reply.GetFailure(s"Cannot found instance [id=$instanceId]") )

      stay() replying re
  }

  def awaitCleanup: StateFunction = {
    case Event(Command.Cleanup(existingIds), Data.Instances(instances)) =>
      val ids = instances map(_.id) diff existingIds
      if(ids.nonEmpty) {
        log.info(s"Found ${ids.size} unused instances, cleanup [ids=$ids]")
        Future { ids map destroyInstance }
      }
      stay()
  }

  def logInstances(instances: InstanceList): Unit = {
    val ids = instances map(_.id)
    log.info(s"Replace instances list [ids=$ids]")
  }

  def destroyInstance(id: String) = {
    val re = cloud.destroy(id)
    log.info(s"Successfuly remove instance [id=$id]")
    re
  }
}

object CloudActor {

  type Instance = AbstractCloud.Instance
  type InstanceList = List[AbstractCloud.Instance]

  def props(cloud: AbstractCloud): Props = Props(new CloudActor(cloud))

  sealed trait State
  object State {
    case object Idle extends State
    case object Active extends State
  }

  sealed trait Data
  object Data {
    case object Empty extends Data
    case class Error(e: String) extends Data
    case class Instances(instances: InstanceList) extends Data
  }

  object Command {
    case object Start
    case class  Create(userId: UUID, role: String, version: Int)
    case object Tick
    case class  Get(id: String)
    case class  Cleanup(existingInstanceIds: List[String])
  }

  object Reply {
    sealed trait StartResult
    case object StartSuccess extends StartResult
    case class  StartFailure(e: String) extends StartResult

    sealed trait CreateResult
    case class CreateSuccess(instance: Instance) extends CreateResult
    case class CreateFailure(e: String)          extends CreateResult

    sealed trait GetResult
    case class GetSuccess(instance: Instance)    extends GetResult
    case class GetFailure(e: String)             extends GetResult
  }
}
