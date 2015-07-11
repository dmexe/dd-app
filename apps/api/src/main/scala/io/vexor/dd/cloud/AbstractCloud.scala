package io.vexor.dd.cloud

import java.util.UUID

import scala.util.Try

object AbstractCloud {

  object Status extends Enumeration {
    val Pending, Active, Broken = Value
  }

  trait Instance {
    val id:     String
    val role:   String
    val status: Status.Value
  }

  trait Provider {
    def create(role: String): Try[Instance]
    def find(id: UUID):   Option[Instance]
  }
}

