package io.vexor.dd.cloud

import java.util.UUID

import AbstractCloud.Status
import scala.util.{Success, Try}

class TestCloud extends AbstractCloud {

  import TestCloud._

  def create(userId: UUID, role: String, version: Int): Try[Instance] = {
    val re = Instance(role, role, new UUID(0,0), role, 1, Status.Pending)
    Success(re)
  }

  def all(): Try[List[Instance]] = {
    Success(List.empty[Instance])
  }

  def destroy(id: String): Try[Boolean] = {
    Success(true)
  }
}

object TestCloud {
  case class Instance(id: String, name: String, userId: UUID, role: String, version: Int, status: Status.Value) extends AbstractCloud.Instance

  def apply() = new TestCloud
}
