package io.vexor.dd.handlers

import spray.json._

trait NodesJsonProtocol extends DefaultJsonProtocol {
  implicit object PutResponseJsonFormat extends RootJsonFormat[Nodes.PutResponse]
  with WriteOnlyJsonProtocol[Nodes.PutResponse] {
    def write(re: Nodes.PutResponse): JsValue = {
      JsObject(
        ("id",         JsString(re.id.toString)),
        ("role",       JsString(re.role)),
        ("state",      JsString(re.state)),
        ("updated_at", JsString(re.updatedAt.toString))
      )
    }
  }
}

