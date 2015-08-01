package io.vexor.docker.api.actors

import java.util.UUID

import akka.actor.{ActorRef, Props, ActorSystem}
import akka.testkit.{TestKit, TestProbe, ImplicitSender, TestKitBase}
import io.vexor.docker.api.TestAppEnv
import io.vexor.docker.api.models.{ModelRegistry, NodesTable, DB}
import scala.concurrent.duration.DurationInt
import org.scalatest.{BeforeAndAfterEach, BeforeAndAfterAll, Matchers, WordSpecLike}

class NodesActorSpec extends TestKitBase with ImplicitSender
with WordSpecLike with Matchers with BeforeAndAfterAll with BeforeAndAfterEach with TestAppEnv {

  val userId     = new UUID(0,0)
  val tableName  = "nodes_node_actor"
  val reg        = ModelRegistry(dbUrl, "NodesActorSpec").get
  val db         = reg.nodes

  val instanceId = "0"
  val role       = "node-actor-spec"
  val newNode    = NodesTable.New(userId, role)

  override def beforeAll() = {
    db.down()
    db.up()
  }

  override def afterAll() = {
    TestKit.shutdownActorSystem(system, duration = 15.seconds)
    db.down()
    reg.db.close()
  }

  override def beforeEach() = {
    db.truncate()
  }

  "A NodesActor" must {
    "successfuly start with empty running nodes" in {
      val cloud = TestProbe()
      val nodesActor = system.actorOf(NodesActor.props(db, cloud.ref))

      nodesActor ! NodesActor.Command.Start
      expectMsg(NodesActor.StartSuccess)
    }

    "successfuly start with some nodes through recovery" in {
      val n1 = db.save(NodesTable.New(userId, "n1")).get
      val n2 = db.save(NodesTable.New(userId, "n2")).get

      val cloud = TestProbe()
      val nodesActor = system.actorOf(NodesActor.props(db, cloud.ref))

      nodesActor ! NodesActor.Command.Start
      expectMsg(NodesActor.StartSuccess)
    }

    "fail to start with error in recovery" in {
      val cloud = TestProbe()
      val child = TestProbe()

      val nodesActor = system.actorOf(Props(new NodesActor(db, cloud.ref){
        override def getNodeActor(userId: UUID, role: String): ActorRef = {
          child.ref
        }
      }))

      db.save(NodesTable.New(userId, "n1")).get
      nodesActor ! NodesActor.Command.Start

      child.expectMsgPF(3.seconds) {
        case NodeActor.Command.Recovery(_) =>
      }

      child.reply(new RuntimeException("noop"))

      expectMsgPF(3.seconds) {
        case NodesActor.StartFailure(_) =>
      }
    }
  }
}