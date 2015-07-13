package io.vexor.dd.actors

import akka.actor.{Props, ActorLogging, Actor}
import scala.concurrent.duration.DurationInt
import io.vexor.dd.cloud.AbstractCloud

class CloudActor(cloud: AbstractCloud) extends Actor with ActorLogging {

  import context.dispatcher
  import CloudActor._

  var instances = List.empty[AbstractCloud.Instance]
  val tick      = context.system.scheduler.schedule(0.seconds, 45.seconds, self, Tick())

  def tickAction(): Unit = {
    cloud.all() foreach { re =>
      instances = re
    }
  }

  def getAllAction(): GetAllResult = {
    GetAllSuccess(instances)
  }

  def receive = {
    case Tick() =>
      tickAction()
    case GetAll() =>
      sender() ! getAllAction()
  }
}

object CloudActor {
  def props(cloud: AbstractCloud): Props = Props(new CloudActor(cloud))

  case class Tick()

  case class GetAll()
  sealed trait GetAllResult
  case class GetAllSuccess(instances: List[AbstractCloud.Instance]) extends GetAllResult
}
