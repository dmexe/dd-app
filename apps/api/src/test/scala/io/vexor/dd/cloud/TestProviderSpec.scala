package io.vexor.dd.cloud

import java.util.{Date, UUID}

import org.scalatest.{FlatSpec, WordSpec, Matchers}
import io.vexor.dd.models.Server

class TestProviderSpec extends WordSpec with Matchers {

  val s = Server.PersistedRecord(
    new UUID(0,0),
    "default",
    Server.Status.New,
    new Date()
  )

  "A TestProvider" must {
    "create a new cloud server" in {
      val re1 = TestProvider.create(s)
      re1 should be(Some(true))

      val re2 = TestProvider.create(s.copy(role = "create-fail"))
      re2 should be(None)
    }

    "fetch ready status" in {
      val re1 = TestProvider.isReady(s)
      re1 should be(Some(true))

      val re2 = TestProvider.isReady(s.copy(role = "is-ready-nook"))
      re2 should be(Some(false))

      val re3 = TestProvider.isReady(s.copy(role = "is-ready-fail"))
      re3 should be(None)
    }
  }
}
