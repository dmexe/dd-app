package io.vexor.cloud.cloud

import java.util.UUID

import scala.util.Try

trait AbstractCloud {

  import AbstractCloud._

  def create(userId: UUID, role: String, version: Int): Try[Instance]
  def all(): Try[List[Instance]]
  def destroy(id: String): Try[Boolean]
}

object AbstractCloud {

  object Status extends Enumeration {
    val Pending, On, Off, Broken = Value
  }

  abstract class Instance {
    val id:      String
    val name:    String
    val userId:  UUID
    val role:    String
    val version: Int
    val status:  Status.Value
  }
}

