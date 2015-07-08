package io.vexor.dd.cloud

import io.vexor.dd.models.Server

object TestProvider {

  def create(server: Server.Persisted): Option[Boolean] = {
    server.role match {
      case "create-fail" => None
      case _             => Some(true)
    }
  }

  def isReady(server: Server.Persisted): Option[Boolean] = {
    server.role match {
      case "is-ready-fail" => None
      case "is-ready-nook" => Some(false)
      case _               => Some(true)
    }
  }
}
