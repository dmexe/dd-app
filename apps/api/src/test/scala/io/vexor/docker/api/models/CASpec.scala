package io.vexor.docker.api.models

import io.vexor.docker.api.TestAppEnv
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class CASpec extends WordSpecLike with Matchers with BeforeAndAfterAll with TestAppEnv {
  val reg = ModelRegistry(dbUrl, "CASpec").get
  val db  = reg.properties

  override def beforeAll() : Unit = {
    db.down()
    db.up()
  }

  override def afterAll() : Unit = {
    db.down()
    reg.db.close()
  }

  "A CA" must {
    "successfuly load, generate and save root certificates" in {
      val ca = CA("id", "subject", db)
      assert(ca.cert.getIssuerDN.toString == "CN=vexor.io")
      assert(ca.cert.getSubjectDN.toString == "CN=subject")

      Thread.sleep(100)

      val nextCa = CA("id", "subject", db)
      assert(ca.cert.getSerialNumber == nextCa.cert.getSerialNumber)
    }
  }
}

