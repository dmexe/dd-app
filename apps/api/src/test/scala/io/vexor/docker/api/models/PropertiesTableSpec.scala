package io.vexor.docker.api.models

import io.vexor.docker.api.TestAppEnv
import io.vexor.docker.api.TestAppEnv
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class PropertiesTableSpec extends WordSpecLike with Matchers with BeforeAndAfterAll with TestAppEnv {
  val reg = ModelRegistry(dbUrl, "PropertiesTableSpec")
  val db  = reg.properties

  override def beforeAll() : Unit = {
    db.down()
    db.up()
  }

  override def afterAll() : Unit = {
    db.down()
    reg.db.close()
  }

  "A PropertiesTable" must {
    "successfuly save and fetch record" in {
      val rec1 = PropertiesTable.Record("name", "value")
      val rec2 = db.save(rec1)
      val rec3 = db.one(rec2.name).get
      assert(rec2 == rec3)
    }
  }
}

