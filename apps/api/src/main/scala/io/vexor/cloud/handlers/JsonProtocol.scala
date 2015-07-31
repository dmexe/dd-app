package io.vexor.cloud.handlers

import io.vexor.cloud.models.NodesTable
import spray.json._

trait JsonProtocol extends DefaultJsonProtocol {

  implicit object NodeJsonFormat extends RootJsonFormat[NodesTable.Persisted]
  with WriteOnlyJsonProtocol[NodesTable.Persisted] {
    def write(re: NodesTable.Persisted): JsValue = {
      JsObject(
        ("user_id",    JsString(re.userId.toString)),
        ("role",       JsString(re.role)),
        ("version",    JsNumber(re.version)),
        ("status",     JsString(re.status.toString)),
        ("created_at", JsString(re.createdAt.toString))
      )
    }
  }
}

