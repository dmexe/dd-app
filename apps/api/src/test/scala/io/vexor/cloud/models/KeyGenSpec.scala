package io.vexor.cloud.models

import io.vexor.cloud.TestAppEnv
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class KeyGenSpec extends WordSpecLike with Matchers with BeforeAndAfterAll with TestAppEnv {

  "A KeyGen" must {
    "successfully generate ca" in {
      val ca = KeyGen.genCa("cloud.vexor.io", "demo")
      val pem = KeyGen.toPEM(ca)
      val newCa = KeyGen.fromPEM(pem)

      assert(newCa.cert       != null)
      assert(newCa.privateKey != null)
      assert(newCa.publicKey  != null)

      assert(ca.cert.toString == newCa.cert.toString)
    }

    "successfully generate client" in {
      val ca     = KeyGen.genCa("cloud.vexor.io", "demo")
      val client = KeyGen.genCert(ca, "client")
      val pem    = KeyGen.toPEM(client)
      val newClient = KeyGen.fromPEM(pem)

      assert(newClient.cert       != null)
      assert(newClient.privateKey != null)
      assert(newClient.publicKey  != null)

      assert(newClient.cert.toString == client.cert.toString)
    }
  }
}