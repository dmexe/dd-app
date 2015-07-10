package io.vexor.dd.cloud

import java.util.{Date, UUID}

import org.scalatest.{WordSpec, Matchers}

class TestProviderSpec extends WordSpec with Matchers {

  val serverId = new UUID(0,0)
  val provider = new TestProvider()

  "A TestProvider" must {
    "create a new cloud server" in {
      val re1 = provider.create(serverId)
      re1 should be(true)
    }
  }
}
