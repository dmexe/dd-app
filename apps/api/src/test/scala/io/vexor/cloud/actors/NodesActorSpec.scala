package io.vexor.cloud.actors

import java.util.UUID

import akka.actor.{ActorRef, Props, ActorSystem}
import akka.testkit.{TestKit, TestProbe, ImplicitSender, TestKitBase}
import io.vexor.cloud.TestAppEnv
import io.vexor.cloud.models.{NodesTable, DB}
import scala.concurrent.duration.DurationInt
import org.scalatest.{BeforeAndAfterEach, BeforeAndAfterAll, Matchers, WordSpecLike}

class NodesActorSpec extends TestKitBase with ImplicitSender
with WordSpecLike with Matchers with BeforeAndAfterAll with BeforeAndAfterEach with TestAppEnv {

  override implicit lazy val system = ActorSystem("NodesActorSpec", appConfig)

  val userId     = new UUID(0,0)
  val tableName  = "nodes_node_actor"
  val db         = new DB(dbUrl).open().get
  val nodesTable = new NodesTable(db, tableName)
  val instanceId = "0"
  val role       = "node-actor-spec"
  val newNode    = NodesTable.New(userId, role)

  override def beforeAll() = {
    nodesTable.down()
    nodesTable.up()
  }

  override def afterAll() = {
    TestKit.shutdownActorSystem(system, duration = 15.seconds)
    nodesTable.down()
  }

  override def beforeEach() = {
    nodesTable.truncate()
  }

  "A NodesActor" must {
    "successfuly start with empty running nodes" in {
      val cloud = TestProbe()
      val nodesActor = system.actorOf(NodesActor.props(nodesTable, cloud.ref))

      nodesActor ! NodesActor.Command.Start
      expectMsg(NodesActor.StartSuccess)
    }

    "successfuly start with some nodes through recovery" in {
      val n1 = nodesTable.save(NodesTable.New(userId, "n1")).get
      val n2 = nodesTable.save(NodesTable.New(userId, "n2")).get

      val cloud = TestProbe()
      val nodesActor = system.actorOf(NodesActor.props(nodesTable, cloud.ref))

      nodesActor ! NodesActor.Command.Start
      expectMsg(NodesActor.StartSuccess)
    }

    "fail to start with error in recovery" in {
      val cloud = TestProbe()
      val child = TestProbe()

      val nodesActor = system.actorOf(Props(new NodesActor(nodesTable, cloud.ref){
        override def getNodeActor(userId: UUID, role: String): ActorRef = {
          child.ref
        }
      }))

      nodesTable.save(NodesTable.New(userId, "n1")).get
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