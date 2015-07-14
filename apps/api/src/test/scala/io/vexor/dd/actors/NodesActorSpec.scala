package io.vexor.dd.actors

import java.util.UUID

import akka.testkit.{ImplicitSender, TestKitBase}
import io.vexor.dd.{Utils, TestAppEnv}
import io.vexor.dd.models.{NodesTable, DB}
import io.vexor.dd.models.NodesTable.Status
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class NodesActorSpec extends TestKitBase with ImplicitSender
with WordSpecLike with Matchers with BeforeAndAfterAll with TestAppEnv {

  val tableName  = "nodes_actor"
  val userId     = new UUID(0,0)
  val db         = new DB(dbUrl).open().get
  val nodesTable = new NodesTable(db, tableName)

  implicit val timeout = Utils.timeoutSec(5)

  override def beforeAll() : Unit = {
    nodesTable.down()
    nodesTable.up()
  }

  override def afterAll() = {
    nodesTable.down()
    system.shutdown()
  }

  "A NodesActorSpec actor" must {

    "successfuly process NewNodes message" in {
      val role  = "new-nodes"
      val actor = system.actorOf(NodesActor.props(nodesTable))

      actor ! NodesActor.NewNodes()
      expectMsgPF(timeout.duration) {
        case NodesActor.NewNodesSuccess(nodes: Seq[NodesTable.Persisted]) =>
          assert(nodes.isEmpty)
        case unknown =>
          fail(s"unknown message $unknown")
      }

      val newNode = NodesTable.New(userId, role)
      val rec = nodesTable.save(newNode).get

      actor ! NodesActor.NewNodes()
      expectMsgPF(timeout.duration) {
        case NodesActor.NewNodesSuccess(nodes: Seq[NodesTable.Persisted]) =>
          assert(nodes == Seq(rec))
        case unknown =>
          fail(s"unknown message $unknown")
      }
    }

    "fail to process UpNode message (create a new node broken)" in {
      val role = "create-broken"
      val fNodesTable = new NodesTable(db, tableName) {
        override def save(n: NodesTable.New): Option[NodesTable.Persisted] = {
          None
        }
      }
      val actor = system.actorOf(NodesActor.props(fNodesTable))

      actor ! NodesActor.UpNode(userId, role)
      expectMsgPF(timeout.duration) {
        case NodesActor.UpNodeFailure(e) =>
          assert(e.isInstanceOf[NodesActor.NodeNotFoundError])
        case unknown =>
          fail(s"unknown message $unknown")
      }

      val allNodes = fNodesTable.allVersionsFor(userId, role)
      assert(allNodes.isEmpty)
    }

    "fail to process UpNode message (update a node status broken)" in {
      val role = "update-broken"
      val fNodesTable = new NodesTable(db, tableName) {
        override def save(prev: NodesTable.Persisted, status: Status.Value = Status.Undefined, cloudId: Option[String] = None): Option[NodesTable.Persisted] = {
          None
        }
      }
      val actor = system.actorOf(NodesActor.props(fNodesTable))

      val newNode = NodesTable.New(userId, role)

      val re1 = nodesTable.save(newNode).get
      nodesTable.save(re1, status = Status.Finished).get

      actor ! NodesActor.UpNode(userId, role)
      expectMsgPF(timeout.duration) {
        case NodesActor.UpNodeFailure(e) =>
          assert(e.isInstanceOf[NodesActor.NodeUpdateError])
        case unknown =>
          fail(s"unknown message $unknown")
      }

      val allNodes = fNodesTable.allVersionsFor(userId, role) map(_.status.toString)
      assert(allNodes == List("Finished", "New"))
    }

    "successfuly process UpNode message" in {
      val role  = "default"
      val actor = system.actorOf(NodesActor.props(nodesTable))

      val receive = () => {
        var re: NodesTable.Persisted = null
        actor ! NodesActor.UpNode(userId, role)
        expectMsgPF(timeout.duration) {
          case NodesActor.UpNodeSuccess(node) =>
            re = node
          case unknown =>
            fail(s"unknown message $unknown")
            re
        }
        re
      }

      val n0 = receive()
      assert(n0.status == Status.New)

      val n1 = receive()
      assert(n1.status == Status.New)

      nodesTable.save(n1, status=Status.Pending)
      val n2 = receive()
      assert(n2.status == Status.Pending)

      nodesTable.save(n2, status=Status.Active)
      val n3 = receive()
      assert(n3.status == Status.Active)

      nodesTable.save(n3, status=Status.Frozen)
      val n4 = receive()
      assert(n4.status == Status.New)

      nodesTable.save(n4, status=Status.Finished)
      val n5 = receive()
      assert(n5.status == Status.New)

      nodesTable.save(n5, status=Status.Broken)
      val n6 = receive()
      assert(n6.status == Status.New)

      val statuses = nodesTable.allVersionsFor(userId, role) map{ v => s"v${v.version}:${v.status}" }
      val expected = List("v9:New", "v8:Broken", "v7:New", "v6:Finished", "v5:New", "v4:Frozen", "v3:Active", "v2:Pending", "v1:New")
      assert(statuses == expected)

    }
  }
}