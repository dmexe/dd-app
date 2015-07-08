package io.vexor.dd.actors

import akka.testkit.{ TestKitBase, ImplicitSender }
import io.vexor.dd.models.{Connector, Server}
import io.vexor.dd.ApplicationEnv
import org.scalatest.WordSpecLike
import org.scalatest.Matchers
import org.scalatest.BeforeAndAfterAll

class RequestNewServerSpec extends TestKitBase with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with ApplicationEnv {

  override def appEnv    = "test"
  override def afterAll  = system.shutdown()
  override def beforeAll = {
    Connector.open(appConfig.getString("cassandra.url"))
    Server.Schema.down()
    Server.Schema.up()
  }

  "A RequestNewServer actor" must {
    "receive New request" in {
      val a = system.actorOf(RequestNewServer.props)
      a ! RequestNewServer.Role("default")
      expectMsg(RequestNewServer.Created("default"))
    }
  }
}
