package io.vexor.dd.handlers

import java.util.{Date, UUID}

import akka.actor.{Props, Actor, ActorLogging}
import akka.pattern.ask
import io.vexor.dd.Utils
import io.vexor.dd.actors.{NodeActor, CloudActor, NodesActor}
import io.vexor.dd.cloud.AbstractCloud
import io.vexor.dd.models.NodesTable
import spray.http.StatusCodes.{UnprocessableEntity, NotFound}
import spray.routing.HttpService

object NodesHandler {

  case class PutResponse(userId: UUID, role: String, version: Int, state: String, createdAt: Date)
  case class InstanceResponse(id: String, name: String, state: String)

  object PutResponse {
    def apply(rec: NodesTable.Persisted): PutResponse =
      PutResponse(rec.userId, rec.role, rec.version, rec.status.toString, rec.createdAt)
  }

  object InstanceResponse {
    def apply(inst: AbstractCloud.Instance): InstanceResponse =
      InstanceResponse(inst.id, inst.name, inst.status.toString)
  }

  class HttpHandler extends Actor with ActorLogging with HttpService with NodesJsonProtocol {

    import spray.httpx.SprayJsonSupport.sprayJsonMarshaller
    import context.dispatcher

    implicit val timeout = Utils.timeoutSec(5)

    val actorRefFactory = context
    val nodesActor      = context.actorSelection("/user/main/nodes")
    val cloudActor      = context.actorSelection("/user/main/cloud")
    val userId          = new UUID(0,0)

    def putNodeAction(role: String) = {
      put {
        onSuccess(nodesActor ? NodesActor.Command.Create(userId, role)) {
          case NodeActor.Reply.CreateSuccess(node) =>
            complete(PutResponse(node))
          case NodeActor.Reply.CreateFailure(e) =>
            complete(UnprocessableEntity, e.getMessage)
        }
      }
    }

    def getNodeAction(role: String) = {
      get {
        onSuccess(nodesActor ? NodesActor.Command.Get(userId, role)) {
          case NodeActor.Reply.GetSuccess(node) =>
            complete(PutResponse(node))
          case NodeActor.Reply.GetFailure(e) =>
            complete(NotFound, e.getMessage)
        }
      }
    }

    def getInstancesAction = {
      get {
        complete("OK")
        /*
        onSuccess(cloudActor ? CloudActor.GetAll()) {
          case CloudActor.GetAllSuccess(instances) =>
            complete(instances map(InstanceResponse(_)))
        }
        */
      }
    }

    def routes = pathPrefix("api" / "v1") {
      logRequestResponse("nodes") {
        path("nodes" / Segment) { role =>
          putNodeAction(role) ~ getNodeAction(role)
        } ~
        path("instances") {
          getInstancesAction
        }
      }
    }

    def receive = runRoute(routes)
  }

  def props : Props = Props(new HttpHandler)
}

