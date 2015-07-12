package io.vexor.dd.handlers

import java.util.{Date, UUID}

import akka.actor.{Props, Actor, ActorLogging}
import akka.pattern.ask
import io.vexor.dd.Utils
import io.vexor.dd.models.NodesTable
import spray.http.StatusCodes.NotFound
import spray.routing.HttpService

object NodesHandler {

  case class PutResponse(id: UUID, role: String, state: String, updatedAt: Date)

  object PutResponse {
    def apply(s: NodesTable.Persisted): PutResponse =
      PutResponse(s.id, s.role, s.status.toString, s.updatedAt)
  }

  class HttpHandler extends Actor with ActorLogging with HttpService with NodesJsonProtocol {

    import spray.httpx.SprayJsonSupport.sprayJsonMarshaller
    import context.dispatcher

    implicit val timeout = Utils.timeoutSec(5)

    val actorRefFactory = context
    val nodesActor = context.actorSelection("/user/main/nodes")

    def putAction(role: String) = {
      put {
        complete("OK")
        /*
        onSuccess(nodesActor ? NodesActor.Up(role)) {
          case NodesActor.Found(s) =>
            complete(PutResponse(s))
          case NodesActor.NotFound(r) =>
            complete(NotFound, s"Cannot found $r")
        }
        */
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

  def props : Props = Props(new HttpHandler)
}

