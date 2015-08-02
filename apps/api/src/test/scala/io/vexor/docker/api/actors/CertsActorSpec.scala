package io.vexor.docker.api.actors

import java.util.UUID

import akka.testkit.{TestKit, ImplicitSender, TestKitBase}
import io.vexor.docker.api.TestAppEnv
import io.vexor.docker.api.models.{CA, ModelRegistry}
import org.scalatest.{BeforeAndAfterEach, BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.concurrent.duration.DurationInt

class CertsActorSpec extends TestKitBase with ImplicitSender
with WordSpecLike with Matchers with BeforeAndAfterAll with TestAppEnv
with BeforeAndAfterEach {

  val reg    = ModelRegistry(dbUrl, "CertsActorSpec")
  val db     = reg.certs
  val userId = new UUID(0,0)
  val role   = "role"

  lazy val ca = CA("id", "subject", reg.properties)

  override def beforeAll() = {
    db.down()
    db.up()
    reg.properties.down()
    reg.properties.up()
  }

  override def afterAll() = {
    TestKit.shutdownActorSystem(system, duration = 15.seconds)
    db.down()
    reg.properties.down()
    reg.db.close()
  }

  override def beforeEach() = {
    db.truncate()
    reg.properties.truncate()
  }

  "A CertsActor" must {
    "receive Command.Get" in {
      val actor = system.actorOf(CertsActor.props(db, ca))

      actor ! CertsActor.Command.Get(userId, role)
      expectMsgPF(3.seconds) {
        case CertsActor.GetSuccess(clientCa, cert, key) =>
          assert(clientCa == ca.certPem)
          cert should include("BEGIN CERTIFICATE")
          key should include("BEGIN RSA PRIVATE KEY")
      }

      actor ! CertsActor.Command.Get(userId, role)
      expectMsgPF(3.seconds) {
        case CertsActor.GetSuccess(clientCa, cert, key) =>
          assert(clientCa == ca.certPem)
          cert should include("BEGIN CERTIFICATE")
          key should include("BEGIN RSA PRIVATE KEY")
      }
    }
  }
}