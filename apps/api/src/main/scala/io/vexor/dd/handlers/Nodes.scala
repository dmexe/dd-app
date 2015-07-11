package io.vexor.dd.handlers

import java.util.{Date, UUID}

import akka.actor.{Props, Actor, ActorLogging}
import akka.pattern.ask
import io.vexor.dd.Utils
import io.vexor.dd.models.Server
import spray.http.StatusCodes.UnprocessableEntity
import spray.routing.HttpService

object Nodes {

  case class PutResponse(id: UUID, role: String, state: String, updatedAt: Date)
  object PutResponse {
    def apply(s: Server.Persisted): PutResponse =
      PutResponse(s.id, s.role, s.status.toString, s.updatedAt)
  }

  class HttpHandler extends Actor with ActorLogging with HttpService with NodesJsonProtocol {

    import spray.httpx.SprayJsonSupport.sprayJsonMarshaller
    import context.dispatcher

    implicit val timeout = Utils.timeoutSec(5)

    val actorRefFactory = context
    val worker = context.actorSelection("/user/main/get-ready-server")

    def putAction(role: String) = {
      put {
        onSuccess(worker ? role) {
          case Some(s: Server.Persisted) =>
            complete(PutResponse(s))
          case default =>
            complete(UnprocessableEntity, default.toString)
        }
      }
    }

    def routes = pathPrefix("api" / "v1") {
      logRequestResponse("nodes") {
        path("nodes" / Segment) { role =>
          putAction(role)
        }
      }
    }

    def receive = runRoute(routes)
  }

  object HttpHandler {
    def props : Props = Props(new HttpHandler)
  }
}

