package io.vexor.cloud

import java.util.UUID

import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class GenClientCertSpec extends WordSpecLike with Matchers with BeforeAndAfterAll with TestAppEnv {

  "A GenClientCert" must {
    "successfully generate client certificate using openssl" in {
      val log    = system.log
      val gen    = new GenClientCert(log, "/Users/dima/apps/dd-app/certs.d/proxy/ca.pem","/Users/dima/apps/dd-app/certs.d/proxy/ca-key.pem", "foobar")
      val userId = new UUID(0,0)
      val role   = "default"
      gen.gen(userId, role)
    }
  }
}