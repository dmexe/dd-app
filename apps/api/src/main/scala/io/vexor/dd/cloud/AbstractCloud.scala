package io.vexor.dd.cloud

import java.util.UUID

import scala.util.Try

trait AbstractCloud {

  import AbstractCloud._

  def create(role: String): Try[Instance]
  def find(id: UUID):   Option[Instance]
}

object AbstractCloud {

  object Status extends Enumeration {
    val Pending, Active, Broken = Value
  }

  trait Instance {
    val id:     String
    val role:   String
    val status: Status.Value
  }
}

