package io.vexor.dd.actors

import java.util.UUID

import akka.actor.{Props, ActorLogging, Actor}
import scala.concurrent.duration.DurationInt
import io.vexor.dd.cloud.AbstractCloud

class CloudActor(cloud: AbstractCloud) extends Actor with ActorLogging {


  import context.dispatcher
  import CloudActor._

  var instances = List.empty[Instance]
  val tick      = context.system.scheduler.schedule(0.seconds, 45.seconds, self, Command.Tick)

  def tickAction(): Unit = {
    cloud.all() foreach { re =>
      instances = re
    }
  }

  def getAllAction(): GetAllResult = {
    GetAllSuccess(instances)
  }

  def receive = {
    case Command.Tick =>
      tickAction()
    case GetAll() =>
      sender() ! getAllAction()
  }
}

object CloudActor {

  type Instance = AbstractCloud.Instance

  def props(cloud: AbstractCloud): Props = Props(new CloudActor(cloud))

  object Command {
    case class  Create(userId: UUID, role: String)
    case object Tick
    case class  Get(id: String)
  }

  object Reply {
    sealed trait CreateResult
    case class CreateSuccess(instance: Instance) extends CreateResult
    case class CreateFailure(e: Throwable)       extends CreateResult

    sealed trait GetResult
    case class GetSuccess(instance: Instance)    extends GetResult
    case class GetFailure(e: Throwable)          extends GetResult
  }

  case class GetAll()
  sealed trait GetAllResult
  case class GetAllSuccess(instances: List[Instance]) extends GetAllResult
}
