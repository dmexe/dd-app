package io.vexor.dd.cloud

import java.util.UUID

import com.myjeeva.digitalocean.impl.DigitalOceanClient
import com.myjeeva.digitalocean.pojo.{Key, Image, Region, Droplet}

import scala.util.Try
import AbstractCloud.Status
import collection.JavaConversions._

class DigitalOceanCloud(token: String, region: String, imageId: Int, keyId: Int, size: String) extends AbstractCloud {
  import DigitalOceanCloud._

  lazy val api = new DigitalOceanClient(token)

  def create(role: String) : Try[Instance] = {
    val newDroplet = new Droplet()
    newDroplet.setName(s"$role.$region.docker")
    newDroplet.setSize(size)
    newDroplet.setRegion(new Region(region))
    newDroplet.setImage(new Image(imageId))

    val keys = List[Key](new Key(keyId))
    newDroplet.setKeys(keys)

    Try(api.createDroplet(newDroplet)) map { d =>
      Instance(d.getId.toString, role, Status.Pending)
    }
  }

  def find(id: UUID) : Option[Instance] = {
    None
  }
}

object DigitalOceanCloud {
  case class Instance(id: String, role: String, status: Status.Value) extends AbstractCloud.Instance
}
