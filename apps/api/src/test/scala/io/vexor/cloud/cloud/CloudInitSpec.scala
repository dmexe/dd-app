package io.vexor.cloud.cloud

import io.vexor.cloud.TestAppEnv
import io.vexor.cloud.models.{CA, ModelRegistry}
import org.scalatest.{BeforeAndAfterEach, BeforeAndAfterAll, Matchers, WordSpecLike}

class CloudInitSpec extends WordSpecLike with Matchers with BeforeAndAfterAll with BeforeAndAfterEach
with TestAppEnv {
  val reg     = ModelRegistry(dbUrl, "CloudInitSpec").get
  val db      = reg.properties

  override def beforeAll() : Unit = {
    db.down()
    db.up()
  }

  override def afterAll() : Unit = {
    db.down()
    reg.db.close()
  }

  override def beforeEach() : Unit = {
    db.truncate()
  }

  "A CloudInit" must {
    "successfuly return content for instance" in {
      val ca = CA("id", "subject", db)
      val ci = CloudInit.docker(ca).get
      val content = ci.getContent("example.com").get
      println(content)
    }

    "fail when file not found" in {
      val ca = CA("id", "subject", db)
      val ci = CloudInit("notfound", ca)
      assert(ci.isFailure)
    }

    "fail when file broken" in {
      val ca = CA("id", "subject", db)
      val ci = CloudInit("broken", ca)
      assert(ci.isFailure)
    }
  }
}

