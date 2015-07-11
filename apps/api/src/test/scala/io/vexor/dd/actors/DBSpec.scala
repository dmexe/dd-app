package io.vexor.dd.actors

import akka.testkit.{ImplicitSender, TestKitBase}
import io.vexor.dd.{TestAppEnv}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration.Duration

class DBSpec extends TestKitBase with ImplicitSender
with WordSpecLike with Matchers with BeforeAndAfterAll with TestAppEnv {

  override def afterAll() = {
    system.shutdown()
  }

  "A DB actor" must {
    "receive Open message and successfuly open and close session" in {
      var session = Option.empty[DB.Session]
      val actor = system.actorOf(DB.props)
      actor ! DB.Open(dbUrl)
      expectMsgPF(Duration(10, "seconds")) {
        case DB.Ready(s) =>
          session = Some(s)
      }
      assert(session.isEmpty == false)
      actor ! DB.Close
      expectMsg(DB.Closed)
    }

    "receive Open message and fail to open session" in {
      val actor = system.actorOf(DB.props)
      actor ! DB.Open("notexists.host")
      expectMsgPF(Duration(10, "seconds")) {
        case DB.OpenFailed(e: Throwable) =>
      }
    }
  }
}