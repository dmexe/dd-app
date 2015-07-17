package io.vexor.cloud.actors

import akka.actor.{ActorRef, Props, ActorLogging, Actor}
import akka.pattern.ask
import io.vexor.cloud.handlers.NodesHandler
import io.vexor.cloud.models.{NodesTable, DB}
import io.vexor.cloud.cloud.AbstractCloud
import io.vexor.cloud.{ConfigRegistry, Utils}
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

      cloudActor = Option(context.actorOf(CloudActor.props(cloud),         "cloud"))
      httpActor  = Option(context.actorOf(NodesHandler.props,              "http"))
      cloudActor foreach {actor =>
        actor ? CloudActor.Command.Start
        nodesActor = Option(context.actorOf(NodesActor.props(nodesTable, actor), "nodes")) map { a =>
          a ? NodesActor.Command.Start
          a
        }
      }

      httpActor map { actor =>
        IO(Http)(context.system) ? Http.Bind(actor, interface = "localhost", port = 3000)
      }

      sender() ! Initialized()
  }
}

