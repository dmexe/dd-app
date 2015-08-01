package io.vexor.docker.api.models

import io.vexor.docker.api.TestAppEnv
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class KeyGenSpec extends WordSpecLike with Matchers with BeforeAndAfterAll with TestAppEnv {

  "A KeyGen" must {
    "successfully generate ca certs" in {
      val ca = KeyGen.genCa("cloud.vexor.io", "demo")
      val pem = KeyGen.toPEM(ca)
      val newCa = KeyGen.fromPEM(pem)

      assert(newCa.cert       != null)
      assert(newCa.privateKey != null)
      assert(newCa.publicKey  != null)

      assert(ca.cert.toString == newCa.cert.toString)
    }

    "successfully generate client certs" in {
      val ca     = KeyGen.genCa("cloud.vexor.io", "demo")
      val client = KeyGen.genCert(ca, "client")
      val pem    = KeyGen.toPEM(client)
      val newClient = KeyGen.fromPEM(pem)

      assert(newClient.cert       != null)
      assert(newClient.privateKey != null)
      assert(newClient.publicKey  != null)

      assert(newClient.cert.toString == client.cert.toString)
    }

    "successfully generate client certs with principal" in {
      val ca     = KeyGen.genCa("cloud.vexor.io", "demo")
      val client = KeyGen.genCert(ca, "CN=cnName, OU=ouName")
      val pem    = KeyGen.toPEM(client)

      val newClient = KeyGen.fromPEM(pem)
      assert(newClient.cert.getSubjectDN.toString == "OU=ouName,CN=cnName")

      assert(newClient.cert       != null)
      assert(newClient.privateKey != null)
      assert(newClient.publicKey  != null)

      assert(newClient.cert.toString == client.cert.toString)
    }
  }
}