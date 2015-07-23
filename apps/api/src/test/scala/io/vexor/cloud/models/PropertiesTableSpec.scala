package io.vexor.cloud.models

import io.vexor.cloud.TestAppEnv
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class PropertiesTableSpec extends WordSpecLike with Matchers with BeforeAndAfterAll with TestAppEnv {
  val _db     = new DB(dbUrl)
  val db      = PropertiesTable(_db.open().get)

  override def beforeAll() : Unit = {
    db.down()
    db.up()
  }

  override def afterAll() : Unit = {
    db.down()
    _db.close()
  }

  "A PropertiesTable" must {
    "successfuly save and fetch record" in {
      val rec1 = PropertiesTable.Record("name", "value")
      val rec2 = db.save(rec1).get
      val rec3 = db.one(rec2.name).get
    }
  }
}

