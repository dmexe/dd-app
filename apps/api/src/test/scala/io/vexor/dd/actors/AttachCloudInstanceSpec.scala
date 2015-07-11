package io.vexor.dd.actors

import java.util.{Date, UUID}
import java.util.concurrent.TimeUnit

import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestKitBase}
import akka.util.Timeout
import io.vexor.dd.AppEnv
import io.vexor.dd.actors.AttachCloudInstance.{Attached, AttachTo}
import io.vexor.dd.cloud.{Status, TestProvider}
import io.vexor.dd.models.Server
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

/*
class AttachCloudInstanceSpec extends TestKitBase with ImplicitSender
with WordSpecLike with Matchers with BeforeAndAfterAll with AppEnv {

  override def appEnv = "test"

  override def afterAll()  = {
    system.shutdown()
  }

  "A AttachCloudInstance actor" must {
    "attach to active instance" in {
      implicit val timeout = Timeout(5, TimeUnit.SECONDS)

      val cloud  = new TestProvider
      val actor  = system.actorOf(AttachCloudInstance.props(cloud))
      val s1 = Server.Persisted(new UUID(1,0), "default", Server.Status.New, new Date())
      val s2 = Server.Persisted(new UUID(2,0), "default", Server.Status.New, new Date())

      actor ! AttachTo(s1)
      expectMsg(Attached(s1.id))

      actor ! AttachTo(s2)
      expectMsg(Attached(s2.id))

      actor ! GetStatusOf(s1.id)
      expectMsg(Status.Active)

      actor ! GetStatusOf(s2.id)
      expectMsg(Status.Active)
    }
  }
}
*/