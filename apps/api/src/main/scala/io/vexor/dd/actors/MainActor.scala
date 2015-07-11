package io.vexor.dd.actors

import akka.actor.{Props, ActorLogging, Actor}
import akka.pattern.ask
import io.vexor.dd.handlers.NodesHandler
import io.vexor.dd.models.DB
import io.vexor.dd.Utils
import akka.io.IO
import spray.can.Http

object MainActor {
  case class Init()
  sealed trait InitResult
  case class Initialized() extends InitResult
  case class InitFailed(e: Throwable) extends InitResult

  def props(db: DB.Session) : Props = Props(new MainActor(db))
}

class MainActor(db: DB.Session) extends Actor with ActorLogging {

  import MainActor._

  implicit val timeout = Utils.timeoutSec(5)

  val nodesActor = context.actorOf(NodesActor.props(db), "nodes")
  val httpActor  = context.actorOf(NodesHandler.props, "http")

  def receive = {
    case Init =>
      IO(Http)(context.system) ? Http.Bind(httpActor, interface = "localhost", port = 3000)
      sender() ! Initialized()
  }
}

