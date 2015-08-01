package io.vexor.docker.api.models

import java.util.UUID

import io.vexor.docker.api.TestAppEnv
import org.scalatest.{BeforeAndAfterEach, BeforeAndAfterAll, Matchers, WordSpecLike}

class CertsTableSpec extends WordSpecLike with Matchers with BeforeAndAfterAll with BeforeAndAfterEach with TestAppEnv {
  val reg    = ModelRegistry(dbUrl, "CertsTableSpec").get
  val db     = reg.certs
  val userId = new UUID(0,0)

  override def beforeAll() : Unit = {
    db.down()
    db.up()
  }

  override def afterAll() : Unit = {
    db.down()
    reg.db.close()
  }

  override def afterEach(): Unit = {
    db.truncate()
  }

  "A CertsTable" must {
    "successfuly save and fetch record" in {
      val rec1 = CertsTable.Record(userId, "role", "cert", "key")

      val rec2 = db.one(userId, "role")
      assert(rec2.isEmpty)

      val rec3 = db.save(rec1).get
      val rec4 = db.one(rec1.userId, rec1.role).get
      assert(rec4 == rec1)
    }
  }
}
