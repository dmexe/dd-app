package io.vexor.cloud.cloud

import io.vexor.cloud.models.{CA,KeyGen}
import io.vexor.cloud.Utils.OptionToTry

import scala.io.Source
import scala.util.Try

class CloudInit(tmplFile: String, tmpl: String, ca: CA) {

  def getContent(fqdn: String) : Try[String] = {
    val cert = KeyGen.genCert(ca.re, fqdn)
    val pem  = KeyGen.toPEM(cert)

    val content =
      replacePreserveSpaces("CA_PEM", ca.certPem, tmpl)
        .flatMap(replacePreserveSpaces("CERT_PEM", pem.cert, _))
        .flatMap(replacePreserveSpaces("KEY_PEM", pem.privateKey, _))

    content.toTry(new RuntimeException(s"Cannot found placeholders CA_PEM, CERT_PEM or KEY_PEM in $tmplFile"))
  }

  def validate(): Try[CloudInit] = {
    getContent("validate") map { _ => this }
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

object CloudInit {
  def apply(role: String, ca: CA): Try[CloudInit] = {
    val resName     = s"/cloudinit/$role.yml"
    val tryFileName = Option(getClass.getResource(resName)).toTry(new RuntimeException(s"Cannot found $resName"))

    for {
      fileName  <- tryFileName
      content   <- Try{ Source.fromURL(fileName).mkString }
      cloudInit <- new CloudInit(fileName.toString, content, ca).validate()
    } yield cloudInit
  }

  def docker(ca: CA) = apply("docker", ca)
}
