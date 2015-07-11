package io.vexor.dd.actors

import java.util.concurrent.TimeUnit

import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestKitBase}
import akka.util.Timeout
import io.vexor.dd.AppEnv
import io.vexor.dd.models.{Server}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class GetReadyServerSpec extends TestKitBase with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with AppEnv {

  /*
  lazy val session = Connector(databaseUrl)
  lazy val db      = Server(session)

  override def appEnv = "test"

  override def afterAll()  = {
    system.shutdown()
    Connector.close(session)
  }

  override def beforeAll() = {
    db.down()
    db.up()
  }

  "A GetReadyServer actor" must {
    "receive server instances" in {
      implicit val timeout = Timeout(5, TimeUnit.SECONDS)

      val a = system.actorOf(GetReadyServer.props(db))
      val re1 = Await.result(a ? "default", Duration(5, "seconds"))

      re1 match {
        case Server.Persisted(id, "default", Server.Status.New, _) => ()
        case _ => fail(s"no match $re1")
      }

      val re2 = Await.result(a ? "default", Duration(5, "seconds"))
      re2 match {
        case Server.Persisted(id, "default", Server.Status.New, _) => ()
        case _ => fail(s"no match $re1")
      }

      re1.asInstanceOf[Server.Persisted].id should be(re2.asInstanceOf[Server.Persisted].id)
    }
  }
*/
}

