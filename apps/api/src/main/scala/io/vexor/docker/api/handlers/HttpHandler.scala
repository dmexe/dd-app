package io.vexor.docker.api.handlers

import java.util.UUID

import akka.actor.{Props, Actor, ActorLogging}
import akka.pattern.ask
import io.vexor.docker.api.DefaultTimeout
import io.vexor.docker.api.actors.{NodeActor, NodesActor}
import io.vexor.docker.api.DefaultTimeout
import io.vexor.docker.api.actors.{NodesActor, NodeActor}
import io.vexor.docker.api.actors.NodesActor.Command
import spray.http.StatusCodes.{UnprocessableEntity, NotFound}
import spray.routing.HttpService

class HttpHandler extends Actor with ActorLogging with HttpService with JsonProtocol with DefaultTimeout {

  import spray.httpx.SprayJsonSupport.sprayJsonMarshaller
  import context.dispatcher

  val actorRefFactory = context
  val userId          = new UUID(0,0)
  val nodesActor      = context.actorSelection("/user/main/nodes")

  def putNodeAction(role: String) = {
    put {
      onSuccess(nodesActor ? Command.Create(userId, role)) {
        case NodeActor.CreateSuccess(node) =>
          complete(node)
        case NodeActor.CreateFailure(e) =>
          complete(UnprocessableEntity, e)
      }
    }
  }

  def getNodeAction(role: String) = {
    get {
      onSuccess(nodesActor ? NodesActor.Command.Get(userId, role)) {
        case NodeActor.GetSuccess(node) =>
          complete(node)
        case NodeActor.GetFailure(e) =>
          complete(NotFound, e)
      }
    }
  }

  def routes = pathPrefix("api" / "v1") {
    logRequestResponse("api") {
      path("nodes" / Segment) { role =>
        putNodeAction(role) ~ getNodeAction(role)
      }
    }
  }

  def receive = runRoute(routes)
}

object HttpHandler {
  def props : Props = Props(new HttpHandler)
}
