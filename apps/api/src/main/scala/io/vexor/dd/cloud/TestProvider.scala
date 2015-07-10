package io.vexor.dd.cloud

import java.util.UUID

object Status extends Enumeration {
  val Pending, Active, Broken = Value
}

case class Instance (
  id:     UUID,
  status: Status.Value
)

trait BaseCloudProvider {
  def create(id: UUID): Boolean
  def find(id: UUID): Option[Instance]
  def status(id: UUID): Option[Status.Value]
}

class TestProvider extends BaseCloudProvider {

  def create(id: UUID) = {
    true
  }

  def find(id: UUID): Option[Instance] = {
    Some(Instance(new UUID(0,0), Status.Active))
  }

  def status(id: UUID): Option[Status.Value] = {
    Some(Status.Active)
  }
}
