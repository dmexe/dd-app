package io.vexor.dd

import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor.{Props, Actor, ActorLogging, ActorSystem}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vexor.dd.actors.{AttachCloudInstance, GetReadyServer}
import io.vexor.dd.models.{Connector, Server}
import spray.routing.HttpService
import spray.can.Http
import spray.http.StatusCodes.{InternalServerError,UnprocessableEntity}

import scala.concurrent.Await
import scala.util.{Success, Failure, Try, Properties}

trait AppEnv {
  implicit lazy val system = ActorSystem(s"dd-$appEnv", appConfig)

  lazy val appConfig = ConfigFactory.load(appEnv)
  lazy val dbUrl     = appConfig.getString("cassandra.url")

  def appEnv = Properties.envOrElse("APP_ENV", "development")
}

trait AppRoutes extends HttpService {

  import spray.json._
  import spray.httpx.SprayJsonSupport._

  object MyJsonProtocol extends DefaultJsonProtocol {

    implicit object DateFormat extends JsonFormat[java.util.Date] {
      val format = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
      override def read(json:JsValue): java.util.Date = format.parse(json.convertTo[String])
      override def write(date:java.util.Date) = format.format(date).toJson
    }

    implicit object UUIDJsonFormat extends JsonFormat[UUID] {
      def write(uuid: UUID): JsValue = JsString(uuid.toString)

      def read(json: JsValue): UUID = json match {
        case JsString(uuidToken) => UUID.fromString(uuidToken)
        case unknown => deserializationError(s"Expected JsString, got $unknown")
      }
    }

    implicit object ServerStatusJsonFormat extends JsonFormat[Server.Status.Value] {
      import Server.Status.Conversions._

      def write(s: Server.Status.Value): JsValue = JsNumber(s.toInt)

      def read(json: JsValue): Server.Status.Value = json match {
        case JsNumber(s) => s.toInt.toValue
        case unknown => deserializationError(s"Expected JsNumber, got $unknown")
      }
    }

    implicit val newFormat       = jsonFormat1(Server.New)
    implicit val persistedFormat = jsonFormat4(Server.Persisted)
  }

}

class AppHandler extends Actor with ActorLogging with AppRoutes {

  val routes = {
    import MyJsonProtocol._
    import spray.httpx.SprayJsonSupport._
    import scala.concurrent.ExecutionContext.Implicits.global

    implicit val timeout = Timeout(5, TimeUnit.SECONDS)

    pathPrefix("api" / "v1") {
      logRequestResponse("nodes") {
        path("nodes" / Segment) { role =>
          get {
            val actor = context.actorFor("/user/get-ready-server")
            onSuccess(actor ? role) {
              case Some(s: Server.Persisted) =>
                complete(s)
              case default =>
                complete(UnprocessableEntity, s"${default}")
            }
          }
        }
      }
    }
  }

  def actorRefFactory = context
  def receive = runRoute(routes)
}

object Main extends App with AppEnv {
  implicit val timeout = Timeout(5, TimeUnit.SECONDS)

  val session = Connector(dbUrl)
  val creator = system.actorOf(GetReadyServer.props(Server(session)), "get-ready-server")
  println(creator)
  val service = system.actorOf(Props[AppHandler], "http")

  IO(Http) ? Http.Bind(service, interface = "localhost", port = 3000)


  /*
  val fu1 = creator ? "default3"
  val fu2 = creator ? "default4"
  val fu3 = creator ? "default5"
  val re1 = Await.result(fu1, timeout.duration)
  val re2 = Await.result(fu2, timeout.duration)
  val re3 = Await.result(fu3, timeout.duration)
  */

  system.awaitTermination()
  // Connector.close(db)
}
