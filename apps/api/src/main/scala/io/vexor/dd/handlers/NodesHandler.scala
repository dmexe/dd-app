package io.vexor.dd.handlers

import java.util.{Date, UUID}

import akka.actor.{Props, Actor, ActorLogging}
import akka.pattern.ask
import io.vexor.dd.Utils
import io.vexor.dd.actors.NodesActor
import io.vexor.dd.models.NodesTable
import spray.http.StatusCodes.{UnprocessableEntity, NotFound}
import spray.routing.HttpService

object NodesHandler {

  case class PutResponse(userId: UUID, role: String, version: Int, state: String, createdAt: Date)

  object PutResponse {
    def apply(rec: NodesTable.Persisted): PutResponse =
      PutResponse(rec.userId, rec.role, rec.version, rec.status.toString, rec.createdAt)
  }

  class HttpHandler extends Actor with ActorLogging with HttpService with NodesJsonProtocol {

    import spray.httpx.SprayJsonSupport.sprayJsonMarshaller
    import context.dispatcher

    implicit val timeout = Utils.timeoutSec(5)

    val actorRefFactory = context
    val nodesActor      = context.actorSelection("/user/main/nodes")
    val userId          = new UUID(0,0)

    def putAction(role: String) = {
      put {
        onSuccess(nodesActor ? NodesActor.Up(userId, role)) {
          case NodesActor.UpSuccess(node) =>
            complete(PutResponse(node))
          case NodesActor.UpFailure(e) =>
            complete(UnprocessableEntity, e.getMessage)
        }
      }
    }

    def getAction(role: String) = {
      get {
        onSuccess(nodesActor ? NodesActor.Get(userId, role)) {
          case NodesActor.GetSuccess(node) =>
            complete(PutResponse(node))
          case NodesActor.GetFailure(e) =>
            complete(NotFound, e.getMessage)
        }
      }
    }

    def routes = pathPrefix("api" / "v1") {
      logRequestResponse("nodes") {
        path("nodes" / Segment) { role =>
          putAction(role) ~ getAction(role)
        }
      }
    }

    def receive = runRoute(routes)
  }

  def props : Props = Props(new HttpHandler)
}

