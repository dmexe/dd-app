package io.vexor.dd.handlers

import spray.json._

trait NodesJsonProtocol extends DefaultJsonProtocol {

  implicit object PutResponseJsonFormat extends RootJsonFormat[NodesHandler.PutResponse]
  with WriteOnlyJsonProtocol[NodesHandler.PutResponse] {
    def write(re: NodesHandler.PutResponse): JsValue = {
      JsObject(
        ("userId",     JsString(re.userId.toString)),
        ("role",       JsString(re.role)),
        ("version",    JsNumber(re.version)),
        ("state",      JsString(re.state)),
        ("created_at", JsString(re.createdAt.toString))
      )
    }
  }

  implicit object GetInstancesJsonFormat extends RootJsonFormat[NodesHandler.InstanceResponse]
  with WriteOnlyJsonProtocol[NodesHandler.InstanceResponse] {
    def write(re: NodesHandler.InstanceResponse): JsValue = {
      JsObject(
        ("id",     JsString(re.id.toString)),
        ("name",       JsString(re.name)),
        ("state",      JsString(re.state))
      )
    }
  }
}

