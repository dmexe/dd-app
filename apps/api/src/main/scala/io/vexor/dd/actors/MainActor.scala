package io.vexor.dd.actors

import akka.actor.{ActorRef, Props, ActorLogging, Actor}
import akka.pattern.ask
import io.vexor.dd.handlers.NodesHandler
import io.vexor.dd.models.{NodesTable, DB}
import io.vexor.dd.cloud.AbstractCloud
import io.vexor.dd.Utils
import akka.io.IO
import spray.can.Http

object MainActor {
  case class Init()
  sealed trait InitResult
  case class Initialized() extends InitResult
  case class InitFailed(e: Throwable) extends InitResult

  def props(db: DB.Session, cloud: AbstractCloud) : Props = Props(new MainActor(db, cloud))
}

class MainActor(db: DB.Session, cloud: AbstractCloud) extends Actor with ActorLogging {

  import MainActor._

  implicit val timeout = Utils.timeoutSec(5)

  val nodesTable = NodesTable(db)

  var nodesActor = Option.empty[ActorRef]
  var cloudActor = Option.empty[ActorRef]
  var httpActor  = Option.empty[ActorRef]

  def receive = {
    case Init =>
      nodesTable.up()

      nodesActor = Option(context.actorOf(NodesActor.props(nodesTable),    "nodes"))
      cloudActor = Option(context.actorOf(CloudActor.props(cloud),         "cloud"))
      httpActor  = Option(context.actorOf(NodesHandler.props,              "http"))

      httpActor map { actor =>
        IO(Http)(context.system) ? Http.Bind(actor, interface = "localhost", port = 3000)
      }

      sender() ! Initialized()
  }
}

