package io.vexor.docker.api.handlers

import java.util.UUID

import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern.ask
import io.vexor.docker.api.DefaultTimeout
import io.vexor.docker.api.actors.{CertsActor, ProxyActor, NodeActor, NodesActor}
import spray.http.StatusCodes.{NotFound, UnprocessableEntity, InternalServerError}
import spray.routing.{HttpService, PathMatcher}

class HttpHandler extends Actor with ActorLogging with HttpService with JsonProtocol with DefaultTimeout {

  import context.dispatcher
  import spray.httpx.SprayJsonSupport.sprayJsonMarshaller

  val actorRefFactory = context
  val userId          = new UUID(0,0)
  val nodesActor      = context.actorSelection("/user/main/nodes")
  val proxyActor      = context.actorSelection("/user/main/proxy")
  val certsActor      = context.actorSelection("/user/main/certs")
  val RoleString      = PathMatcher("""[\da-zA-Z-]{2,36}""".r)

  def putNodeAction(role: String) = {
    put {
      onSuccess(nodesActor ? NodesActor.Command.Create(userId, role)) {
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

  def getProxyCredentialsAction(subject: String) = {
    get {
      onSuccess(proxyActor ? ProxyActor.Command.Credentials(subject)) {
        case x: ProxyActor.CredentialsSuccess =>
          complete(x)
        case e =>
          complete(UnprocessableEntity, e.toString)
      }
    }
  }

  def postCertsAction(userId: UUID, role: String) = {
    post {
      onSuccess(certsActor ? CertsActor.Command.Get(userId, role)) {
        case x: CertsActor.GetSuccess =>
          complete(x)
        case e =>
          complete(UnprocessableEntity, e.toString)
      }
    }
  }

  def routes = pathPrefix("api" / "v1") {
    logRequestResponse("api") {
      path("nodes" / RoleString) { role =>
        putNodeAction(role) ~ getNodeAction(role)
      } ~
      pathPrefix("proxy") {
        path("credentials" / Segment) { subject =>
          getProxyCredentialsAction(subject)
        }
      } ~
      pathPrefix("certs" / JavaUUID / RoleString) { (userId, role) =>
        postCertsAction(userId, role)
      }
    }
  }

  def receive = runRoute(routes)
}

object HttpHandler {
  def props : Props = Props(new HttpHandler)
}
