package io.vexor.dd.models

import io.vexor.dd.TestAppEnv
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class DBSpec extends WordSpecLike with Matchers with BeforeAndAfterAll with TestAppEnv {

  "A DB actor" must {
    "successfully open and close session" in {
      val db = new DB()
      db.open(dbUrl)

      assert(db.isOpen)

      db.close()
    }

    "fail to open session" in {
      val db = new DB()
      db.open("bad.url")

      assert(db.isOpen == false)
    }
  }
}