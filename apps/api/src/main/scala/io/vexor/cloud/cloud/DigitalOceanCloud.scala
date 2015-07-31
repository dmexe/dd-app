package io.vexor.cloud.cloud

import java.util.UUID

import com.myjeeva.digitalocean.impl.DigitalOceanClient
import com.myjeeva.digitalocean.pojo.{Droplet, Image, Key, Region}
import io.vexor.cloud.cloud.AbstractCloud.Status
import org.apache.http.client.config.RequestConfig
import org.apache.http.impl.client.{CloseableHttpClient, HttpClientBuilder}

import scala.annotation.tailrec
import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.util.Try

class DigitalOceanCloud(token: String, region: String, imageId: Int, keyId: Int, size: String, cloudInit: CloudInit) extends AbstractCloud {
  import DigitalOceanCloud._

  lazy val api  = new DigitalOceanClient("v2", token, buildHttpClient())

  def create(userId: UUID, role: String, version: Int) : Try[Instance] = {
    val fqdn       = s"$userId.$role.v$version.$region.docker"
    val userData   = cloudInit.getContent(fqdn)

    for {
      userData <- cloudInit.getContent(fqdn)
      instance <- createDroplet(userId, role, version, fqdn, userData)
    } yield instance
  }

  def all(): Try[List[Instance]] = {
    val fu = Future { allAvailableDroplets() }
    Try { Await.result(fu, opTimeout) }
  }

  def destroy(id: String): Try[Boolean] = {
    val fu = Future { api.deleteDroplet(id.toInt) }
    val re = Try { Await.result(fu, opTimeout) }
    re map (_.getIsRequestSuccess)
  }

  private def createDroplet(userId: UUID, role: String, version: Int, fqdn: String, userData: String): Try[Instance] = {
    val newDroplet = new Droplet()
    newDroplet.setName(fqdn)
    newDroplet.setSize(size)
    newDroplet.setRegion(new Region(region))
    newDroplet.setImage(new Image(imageId))
    newDroplet.setUserData(userData)

    val keys = List[Key](new Key(keyId))
    newDroplet.setKeys(keys)

    val fu = Future { api.createDroplet(newDroplet) }
    val re = Try { Await.result(fu, opTimeout) }
    re map { d =>
      Instance(d.getId.toString, d.getName, userId, role, version, Status.Pending)
    }
  }

  private def dropletToInstance(droplet: Droplet): Option[Instance] = {
    val name   = droplet.getName
    nameRe.findFirstMatchIn(name) map { i =>
      val userId  = i.group(1: Int)
      val role    = i.group(2: Int)
      val version = i.group(3: Int)
      val id      = droplet.getId.toString
      val status  = droplet.getStatus.toString match {
        case "new"     => Status.Pending
        case "active"  => Status.On
        case "off"     => Status.Off
        case "archive" => Status.Off
        case _         => Status.Broken
      }
      Instance(id, name, UUID.fromString(userId), role, version.toInt, status)
    }
  }

  @tailrec
  private def allAvailableDroplets(collected:List[Instance] = List.empty[Instance], pageNo:Int = 1): List[Instance] = {
    val newDroplets = api.getAvailableDroplets(pageNo).getDroplets.toList

    if(newDroplets.isEmpty) {
      collected
    } else {
      val newInstances = newDroplets.flatMap(dropletToInstance) ++ collected
      allAvailableDroplets(newInstances, pageNo + 1)
    }
  }
}

object DigitalOceanCloud {
  val opTimeout = 5.seconds
  val nameRe    = """^([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\.(\w+)\.v(\d+)""".r

  case class Instance(id: String, name: String, userId: UUID, role:String, version:Int, status: Status.Value) extends AbstractCloud.Instance

  private def buildHttpClient(): CloseableHttpClient = {
    val httpCfg = RequestConfig.custom()
    httpCfg.setConnectTimeout(opTimeout.toMillis.toInt)
    httpCfg.setSocketTimeout(opTimeout.toMillis.toInt)

    val httpBuilder = HttpClientBuilder.create()
    httpBuilder.setDefaultRequestConfig(httpCfg.build())

    httpBuilder.build()
  }
}
