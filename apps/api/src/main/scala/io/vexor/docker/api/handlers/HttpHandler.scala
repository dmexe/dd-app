package io.vexor.docker.api.handlers

import java.util.UUID

import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern.ask
import io.vexor.docker.api.DefaultTimeout
import io.vexor.docker.api.actors.NodesActor.Command
import io.vexor.docker.api.actors.{NodeActor, NodesActor}
import spray.http.StatusCodes.{NotFound, UnprocessableEntity}
import spray.routing.{HttpService, PathMatcher}

class HttpHandler extends Actor with ActorLogging with HttpService with JsonProtocol with DefaultTimeout {

  import context.dispatcher
  import spray.httpx.SprayJsonSupport.sprayJsonMarshaller

  val actorRefFactory = context
  val userId          = new UUID(0,0)
  val nodesActor      = context.actorSelection("/user/main/nodes")
  val RoleString      = PathMatcher("""[\da-zA-Z-]{2,36}""".r)

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

  def getDockerAction = {
    get {
      complete("OK")
    }
  }

  def putDockerStatsAction = {
    path("stats" / JavaUUID / RoleString) { (userId, role) =>
      put {
        complete("OK")
      }
    }
  }

  def routes = pathPrefix("api" / "v1") {
    logRequestResponse("api") {
      path("nodes" / RoleString) { role =>
        putNodeAction(role) ~ getNodeAction(role)
      } ~
      pathPrefix("docker") {
        getDockerAction ~ putDockerStatsAction
      }
    }
  }

  def receive = runRoute(routes)
}

object HttpHandler {
  def props : Props = Props(new HttpHandler)
}
