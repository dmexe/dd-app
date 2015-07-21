package io.vexor.cloud

import java.util.UUID
import akka.event.LoggingAdapter

import scala.sys.process._
import java.io.{PrintWriter, File}

import scala.util.{Success, Try, Failure}

object GenClientCert {
}

class GenClientCert(log: LoggingAdapter, clientCa: String, clientCaKey: String, clientCaPass: String) {
  def gen(userId: UUID, role: String): Unit = {
    val id     = s"$userId.$role"
    val key    = File.createTempFile(id, ".key")
    val scr    = File.createTempFile(id, ".scr")
    val cert   = File.createTempFile(id, ".cert")
    val ext    = File.createTempFile(id, ".ext")
    val serial = File.createTempFile(id, ".serial")

    val extWriter = new PrintWriter(new File(ext.toString))
    extWriter.write("extendedKeyUsage = clientAuth")
    extWriter.close()

    for {
      a <- openssl("genrsa -out %s 2048".format(key))
      b <- openssl("req -subj /CN=%s@cloud.vexor.io -new -key %s -out %s".format(id, key, scr))
      c <- openssl("x509 -req -days 365 -CAcreateserial -in %s -CA %s -CAkey %s -out %s -extfile %s -passin pass:%s".format(scr, clientCa, clientCaKey, cert, ext, clientCaPass))
    } yield a
  }

  def openssl(in: String): Option[Int] = {
    val qb = Process(s"openssl $in")
    var out = List[String]()

    val exit = qb ! ProcessLogger((s) => out ::= s, (s) => out ::= s)

    log.info(s"[openssl] > $in ($exit)")

    out.reverse.foreach(l => log.info(s"[openssl] < $l"))

    if (exit == 0) {
      Some(exit)
    } else {
      None
    }
  }
}
