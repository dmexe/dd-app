package io.vexor.cloud

import scala.io.Source
import scala.util.{Try,Success,Failure}

object ConfigRegistry {

  val dockerCaPem      = "ca.pem"
  val dockerServerKey  = "server-key.pem"
  val dockerServerCert = "server-cert.pem"

  def load(caDir: String, cloudInitDir: String): Try[ConfigRegistry] = {
    Try{
      val ca   = Source.fromFile(s"$caDir/$dockerCaPem").mkString
      val key  = Source.fromFile(s"$caDir/$dockerServerKey").mkString
      val cert = Source.fromFile(s"$caDir/$dockerServerCert").mkString
      val cloudInitDocker = Source.fromFile(s"$cloudInitDir/docker.yml").mkString

      val cloudInitContent =
        replacePreserveSpaces("DOCKER_CA_PEM", ca, cloudInitDocker)
          .flatMap(replacePreserveSpaces("DOCKER_SERVER_CERT_PEM", cert, _))
          .flatMap(replacePreserveSpaces("DOCKER_SERVER_KEY_PEM", key, _))

      if(cloudInitContent.isEmpty) {
        throw new RuntimeException(s"Cannot found a placeholders in $cloudInitDocker file")
      }

      new ConfigRegistry(ca, key, cert, cloudInitContent.get)
    }
  }

  private def replacePreserveSpaces(key:String, replacement: String, content:String): Option[String] = {
    val re = s"""( +)(%$key%).*""".r

    re.findFirstMatchIn(content)
      .map{ found =>
        val space    = found.group(1)
        val newRepl  = replacement.lines.map{line => space + line + "\n"}.mkString
        content.replaceAll(re.toString(), newRepl)
      }
  }
}

case class ConfigRegistry(
  dockerCaPem:      String,
  dockerServerKey:  String,
  dockerServerCert: String,
  cloudInitDocker:  String
)
