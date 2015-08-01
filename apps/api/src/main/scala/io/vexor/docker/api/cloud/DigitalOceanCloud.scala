package io.vexor.docker.api.cloud

import java.time.Instant
import java.util.{Date, UUID}

import com.myjeeva.digitalocean.impl.DigitalOceanClient
import com.myjeeva.digitalocean.pojo.{Droplet, Image, Key, Region}
import io.vexor.docker.api.cloud.AbstractCloud.Status
import io.vexor.docker.api.Utils.OptionToTry
import io.vexor.docker.api.models.SshKey
import org.apache.http.client.config.RequestConfig
import org.apache.http.impl.client.{CloseableHttpClient, HttpClientBuilder}

import scala.annotation.tailrec
import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.util.{Try, Success, Failure}

class DigitalOceanCloud(token: String, region: String, size: String, cloudInit: CloudInit, sshKey: SshKey) extends AbstractCloud {
  import DigitalOceanCloud._

  lazy val api  = new DigitalOceanClient("v2", token, buildHttpClient())

  var cachedKey: Try[Key] = Failure(new RuntimeException("Key not cached"))

  def create(userId: UUID, role: String, version: Int) : Try[Instance] = {
    val fqdn       = s"$userId.$role.v$version.$region.docker"
    val userData   = cloudInit.getContent(fqdn)

    for {
      image    <- getDockerImage
      key      <- getDropletKey
      userData <- cloudInit.getContent(fqdn)
      instance <- createDroplet(userId, role, version, fqdn, userData, image, key)
    } yield instance
  }

  def all(): Try[List[Instance]] = {
    val fu = Future { getAvailableDroplets() }
    Try { Await.result(fu, opTimeout) }
  }

  def destroy(id: String): Try[Boolean] = {
    val fu = Future { api.deleteDroplet(id.toInt) }
    val re = Try { Await.result(fu, opTimeout) }
    re map (_.getIsRequestSuccess)
  }

  private def createDroplet(userId: UUID, role: String, version: Int, fqdn: String, userData: String, image: Image, key: Key): Try[Instance] = {
    val newDroplet = new Droplet()
    newDroplet.setName(fqdn)
    newDroplet.setSize(size)
    newDroplet.setRegion(new Region(region))
    newDroplet.setImage(image)
    newDroplet.setUserData(userData)

    val keys = List[Key](key)
    newDroplet.setKeys(keys)

    val fu = Future { api.createDroplet(newDroplet) }
    val re = Try { Await.result(fu, opTimeout) }
    re map { d =>
      val tm   = Instant.ofEpochMilli(d.getCreatedDate.getTime)
      val addr = d.getNetworks.getVersion4Networks.head.getIpAddress
      Instance(d.getId.toString, d.getName, addr, userId, role, version, Status.Pending, tm)
    }
  }

  private def dropletToInstance(droplet: Droplet): Option[Instance] = {
    val name   = droplet.getName
    nameRe.findFirstMatchIn(name) map { i =>
      val userId  = i.group(1: Int)
      val role    = i.group(2: Int)
      val version = i.group(3: Int)
      val id      = droplet.getId.toString
      val tm      = Instant.ofEpochMilli(droplet.getCreatedDate.getTime)
      val addr    = droplet.getNetworks.getVersion4Networks.head.getIpAddress
      val status  = droplet.getStatus.toString match {
        case "new"     => Status.Pending
        case "active"  => Status.On
        case "off"     => Status.Off
        case "archive" => Status.Off
        case _         => Status.Broken
      }
      Instance(id, name, addr, UUID.fromString(userId), role, version.toInt, status, tm)
    }
  }

  private def getDockerImage: Try[Image] = {
    val images = getAvailableImages()
    images
      .filter(_.getRegions.toList.contains(region))
      .find(_.getSlug == "coreos-stable")
      .toTry(s"Cannot found image with slug=coreos-stable and region=$region")
  }

  private def getDropletKey: Try[Key] = {
    cachedKey orElse loadKey() orElse createKey()
  }

  private def loadKey(): Try[Key] = {
    val keyName  = getClass.getName
    val keys     = getAvailableKeys()
    val maybeKey = keys.find(_.getName == keyName)
    maybeKey.foreach { key =>
      cachedKey = Success(key)
    }
    maybeKey.toTry(s"Key $keyName not found")
  }

  private def createKey(): Try[Key] = {
    val keyName = getClass.getName
    val newKey = new Key()
    newKey.setName(keyName)
    newKey.setPublicKey(sshKey.publicKey)
    val tryKey = Try{ api.createKey(newKey) }
    tryKey.foreach{ key =>
      cachedKey = Success(key)
    }
    tryKey
  }

  @tailrec
  private def getAvailableImages(collected: List[Image] = List.empty[Image], pageNo: Int = 1): List[Image] = {
    val newImages = api.getAvailableImages(pageNo).getImages.toList
    if (newImages.isEmpty) {
      collected
    } else {
      getAvailableImages(newImages ++ collected, pageNo + 1)
    }
  }

  @tailrec
  private def getAvailableDroplets(collected:List[Instance] = List.empty[Instance], pageNo:Int = 1): List[Instance] = {
    val newDroplets = api.getAvailableDroplets(pageNo).getDroplets.toList

    if(newDroplets.isEmpty) {
      collected
    } else {
      val newInstances = newDroplets.flatMap(dropletToInstance) ++ collected
      getAvailableDroplets(newInstances, pageNo + 1)
    }
  }

  @tailrec
  private def getAvailableKeys(collected:List[Key] = List.empty[Key], pageNo:Int = 1): List[Key] = {
    val newKeys = api.getAvailableKeys(pageNo).getKeys.toList
    if (newKeys.isEmpty) {
      collected
    } else {
      getAvailableKeys(newKeys ++ collected, pageNo + 1)
    }
  }
}

object DigitalOceanCloud {
  val opTimeout = 5.seconds
  val nameRe    = """^([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\.(\w+)\.v(\d+)""".r

  case class Instance(id: String, name: String, addr: String, userId: UUID, role:String, version:Int, status: Status.Value, createdAt:Instant) extends AbstractCloud.Instance

  private def buildHttpClient(): CloseableHttpClient = {
    val httpCfg = RequestConfig.custom()
    httpCfg.setConnectTimeout(opTimeout.toMillis.toInt)
    httpCfg.setSocketTimeout(opTimeout.toMillis.toInt)

    val httpBuilder = HttpClientBuilder.create()
    httpBuilder.setDefaultRequestConfig(httpCfg.build())

    httpBuilder.build()
  }
}
