package io.vexor.docker.api.actors

import java.time.OffsetDateTime
import java.util.UUID

import akka.actor.{ActorLogging, FSM, Props}
import io.vexor.docker.api.cloud.AbstractCloud

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

class CloudActor(cloud: AbstractCloud) extends FSM[CloudActor.State, CloudActor.Data] with ActorLogging {

  import CloudActor._
  import context.dispatcher

  val tickInterval      = 20.seconds
  val gapCleanupMinutes = 1

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
          goto(State.Active) using Data.Instances(instances) replying StartSuccess
        case error =>
          goto(State.Idle) using Data.Error(error.toString) replying StartFailure(error.toString)
      }
  }

  def awaitCreate: StateFunction = {
    case Event(Command.Create(userId, role, version), Data.Instances(instances)) =>
      cloud.create(userId, role, version) match {
        case Success(instance: Instance) =>
          val newInstances = instances ++ List(instance)
          logInstances(newInstances)
          stay() using Data.Instances(newInstances) replying CreateSuccess(instance)
        case Failure(error) =>
          throw error
          stay() replying CreateFailure(error.toString)
      }
  }

  def awaitTick: StateFunction = {
    case Event(Command.Tick, Data.Instances(oldInstances)) =>
      val newInstances =
        cloud.all() match {
          case Success(i: InstanceList) => i
          case _ => oldInstances
        }
      if (newInstances.map(_.id).sorted != oldInstances.map(_.id).sorted) {
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
        .map       ( GetSuccess  )
        .getOrElse ( GetFailure(s"Cannot found instance [id=$instanceId]") )

      stay() replying re
  }

  def awaitCleanup: StateFunction = {
    case Event(Command.Cleanup(existingIds), Data.Instances(instances)) =>
      val gap = OffsetDateTime.now().minusMinutes(gapCleanupMinutes).toInstant

      val ids = instances filter(_.createdAt.isBefore(gap)) map(_.id) diff existingIds
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
    case class  Error(e: String) extends Data
    case class  Instances(instances: InstanceList) extends Data
  }

  object Command {
    case object Start
    case class  Create(userId: UUID, role: String, version: Int)
    case object Tick
    case class  Get(id: String)
    case class  Cleanup(existingInstanceIds: List[String])
  }

  sealed trait StartReply
  case object StartSuccess extends StartReply
  case class  StartFailure(e: String) extends StartReply

  sealed trait CreateReply
  case class CreateSuccess(instance: Instance) extends CreateReply
  case class CreateFailure(e: String)          extends CreateReply

  sealed trait GetReply
  case class GetSuccess(instance: Instance)    extends GetReply
  case class GetFailure(e: String)             extends GetReply
}
