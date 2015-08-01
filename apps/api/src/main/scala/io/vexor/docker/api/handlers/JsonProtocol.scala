package io.vexor.docker.api.handlers

import java.time.Instant
import java.util.{Date, UUID}

import io.vexor.docker.api.actors.{CertsActor, ProxyActor}
import io.vexor.docker.api.cloud.AbstractCloud
import io.vexor.docker.api.models.NodesTable
import spray.json._

trait WriteOnlyJsonProtocol[T] {
  def read(json: JsValue): T = {
    deserializationError("Write only record")
  }
}

trait WriteToStringJsonProtocol[T] {
  def write(obj: T) = JsString(obj.toString)
}

trait JsonProtocol extends DefaultJsonProtocol {
  implicit object UUIDJsonFormat extends RootJsonFormat[UUID]
    with WriteOnlyJsonProtocol[UUID]
    with WriteToStringJsonProtocol[UUID]
  implicit object NodeStatusJsonFormat extends RootJsonFormat[NodesTable.Status.Value]
    with WriteOnlyJsonProtocol[NodesTable.Status.Value]
    with WriteToStringJsonProtocol[NodesTable.Status.Value]
  implicit object DateJsonFormat extends RootJsonFormat[Date]
    with WriteOnlyJsonProtocol[Date]
    with WriteToStringJsonProtocol[Date]
  implicit object InstantJsonFormat extends RootJsonFormat[Instant]
    with WriteOnlyJsonProtocol[Instant]
    with WriteToStringJsonProtocol[Instant]

  implicit object cloudInstanceJsonFormat extends RootJsonFormat[AbstractCloud.Instance]
    with WriteOnlyJsonProtocol[AbstractCloud.Instance] {
    def write(obj: AbstractCloud.Instance): JsObject = {
      JsObject(
        "id"        -> JsString(obj.id),
        "status"    -> JsString(obj.status.toString),
        "addr"      -> JsString(obj.addr),
        "createdAt" -> JsString(obj.createdAt.toString)
      )
    }
  }

  implicit def dockerTlsInfoFormat            = jsonFormat3(ProxyActor.TlsInfo)
  implicit def dockerCredentialsSuccessFormat = jsonFormat2(ProxyActor.CredentialsSuccess)
  implicit def persistedNodeFormat            = jsonFormat6(NodesTable.Persisted)
  implicit def certsGetSuccessFormat          = jsonFormat3(CertsActor.GetSuccess)
}
