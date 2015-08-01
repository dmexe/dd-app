package io.vexor.docker.api.handlers

import spray.json._

trait WriteOnlyJsonProtocol[T] {
  def read(json: JsValue): T = {
    deserializationError("Write only record")
  }
}

