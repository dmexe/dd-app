package io.vexor.docker.api.cloud

import java.time.Instant
import java.util.UUID

import AbstractCloud.Status
import scala.util.{Success, Try}

class TestCloud extends AbstractCloud {

  import TestCloud._

  def create(userId: UUID, role: String, version: Int): Try[Instance] = {
    val re = Instance(role, role, "127.0.0.1", new UUID(0,0), role, 1, Status.Pending, Instant.now())
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
  case class Instance(id: String, name: String, addr: String, userId: UUID, role: String, version: Int, status: Status.Value, createdAt: Instant) extends AbstractCloud.Instance

  def apply() = new TestCloud
}
