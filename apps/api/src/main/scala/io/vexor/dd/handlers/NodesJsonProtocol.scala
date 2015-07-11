package io.vexor.dd.handlers

import spray.json._

trait NodesJsonProtocol extends DefaultJsonProtocol {
  implicit object PutResponseJsonFormat extends RootJsonFormat[NodesHandler.PutResponse]
  with WriteOnlyJsonProtocol[NodesHandler.PutResponse] {
    def write(re: NodesHandler.PutResponse): JsValue = {
      JsObject(
        ("id",         JsString(re.id.toString)),
        ("role",       JsString(re.role)),
        ("state",      JsString(re.state)),
        ("updated_at", JsString(re.updatedAt.toString))
      )
    }
  }
}

