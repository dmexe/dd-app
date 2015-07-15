package io.vexor.dd.actors

import java.util.UUID

import akka.actor.ActorSystem
import akka.testkit._
import io.vexor.dd.TestAppEnv
import io.vexor.dd.cloud.{TestCloud, AbstractCloud}
import io.vexor.dd.models.{NodesTable, DB}
import org.scalatest.{BeforeAndAfterEach, BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.concurrent.duration.DurationInt
import io.vexor.dd.actors.NodeActor.{Command,Reply,State,Data}

class NodeActorSpec extends TestKitBase with ImplicitSender
with WordSpecLike with Matchers with BeforeAndAfterAll with BeforeAndAfterEach with TestAppEnv {

  override implicit lazy val system = ActorSystem("NodeActorSpec", appConfig)

  val userId     = new UUID(0,0)
  val tableName  = "nodes_node_actor"
  val db         = new DB(dbUrl).open().get
  val nodesTable = new NodesTable(db, tableName)
  val instanceId = "0"
  val role       = "node-actor-spec"
  val newNode    = NodesTable.New(userId, role)

  val pendingInstance  = TestCloud.Instance(instanceId, "name", userId, role, 1, AbstractCloud.Status.Pending)
  val activeInstance   = pendingInstance.copy(status = AbstractCloud.Status.On)
  val inactiveInstance = pendingInstance.copy(status = AbstractCloud.Status.Off)
  val brokenInstance   = pendingInstance.copy(status = AbstractCloud.Status.Broken)

  override def beforeAll() = {
    nodesTable.down()
    nodesTable.up()
  }

  override def afterAll() = {
    nodesTable.down()
    TestKit.shutdownActorSystem(system)
  }

  override def beforeEach() = {
    nodesTable.truncate()
  }

  "A NodeActor actor" must {
    "successfuly create and processing node" in {
      val cloudActor = TestProbe()
      val nodeActor  = system.actorOf(NodeActor.props(nodesTable, cloudActor.ref))

      // Idle: state must be Idle
      nodeActor ! Command.Status
      expectMsg(Reply.StatusSuccess(State.Idle, Data.Empty))

      // Idle: send create
      nodeActor ! Command.Create(newNode)
      expectMsgPF(5.seconds) {
        case Reply.CreateSuccess(node) =>
          assert(node.status == NodesTable.Status.New)
      }

      // New: create instance
      cloudActor.expectMsgPF(5.seconds) {
        case CloudActor.Command.Create(i, r) =>
          assert(i == userId)
          assert(r == role)
      }

      // New: reply instance if pending
      cloudActor.reply(CloudActor.Reply.CreateSuccess(pendingInstance))

      // Pending: state must be Pending
      nodeActor ! Command.Status
      expectMsgPF(5.seconds) {
        case Reply.StatusSuccess(State.Pending, Data.Node(node)) =>
          assert(node.status == NodesTable.Status.Pending)
      }

      // Pending: await instance
      for (i <- 0 to 2) {
        cloudActor.expectMsgPF(10.seconds) {
          case CloudActor.Command.Get(id) =>
            assert(id == instanceId)
        }
        if (i == 2) {
          cloudActor.reply(CloudActor.Reply.GetSuccess(activeInstance))
        } else {
          cloudActor.reply(CloudActor.Reply.GetSuccess(pendingInstance))
        }
      }

      // Active: state must be Active
      nodeActor ! Command.Status
      expectMsgPF(5.seconds) {
        case Reply.StatusSuccess(State.Active, Data.Node(node)) =>
          assert(node.status == NodesTable.Status.Active)
      }

      // Active: await instance termination
      for (i <- 0 to 2) {
        cloudActor.expectMsgPF(10.seconds) {
          case CloudActor.Command.Get(id) =>
            assert(id == instanceId)
        }
        if (i == 2) {
          cloudActor.reply(CloudActor.Reply.GetSuccess(inactiveInstance))
        } else {
          cloudActor.reply(CloudActor.Reply.GetSuccess(activeInstance))
        }
      }

      // Shutdown: state must be Shutdown
      nodeActor ! Command.Status
      expectMsg(Reply.StatusSuccess(State.Shutdown, Data.Empty))

      // Idle: state must be Idle
      nodeActor ! Command.Status
      expectMsg(Reply.StatusSuccess(State.Idle, Data.Empty))

      // Check persisted versions
      val versions = nodesTable.allVersionsFor(userId, role) map(v => (v.version, v.status.toString))
      val expected = List((4,"Finished"), (3,"Active"), (2,"Pending"), (1,"New"))
      assert(versions == expected)
    }

    "fail when node wasn't created (State.Idle)" in {
      val cloudActor  = TestProbe()
      val fNodesTable = new NodesTable(db, tableName) {
        override def save(node: NodesTable.New): Option[NodesTable.Persisted] = None
      }
      val nodeActor   = system.actorOf(NodeActor.props(fNodesTable, cloudActor.ref))

      // Idle: send create and get fail
      nodeActor ! Command.Create(newNode)
      expectMsgPF(5.seconds) {
        case Reply.CreateFailure(error) =>
      }

      // Idle: stay in Idle
      nodeActor ! Command.Status
      expectMsg(Reply.StatusSuccess(State.Idle, Data.Empty))

      // Check persisted versions
      val versions = nodesTable.allVersionsFor(userId, role)
      assert(versions == List.empty)
    }

    "fail when instance wasn't created: error in cloud provider (State.New)" in {
      val cloudActor  = TestProbe()
      val nodeActor   = system.actorOf(NodeActor.props(nodesTable, cloudActor.ref))

      // Idle: send create command
      nodeActor ! Command.Create(newNode)
      expectMsgPF(5.seconds) {
        case Reply.CreateSuccess(_) =>
      }

      // New: create instance
      cloudActor.expectMsgPF(5.seconds) {
        case CloudActor.Command.Create(_, _) =>
      }

      // New: reply instance if fail
      cloudActor.reply(CloudActor.Reply.CreateFailure(new RuntimeException("noop")))

      // Shutdown: state must be Shutdown
      nodeActor ! Command.Status
      expectMsgPF(5.seconds) {
        case Reply.StatusSuccess(State.Shutdown, Data.Error(_)) =>
      }

      // Check persisted versions
      val versions = nodesTable.allVersionsFor(userId, role) map(v => (v.version, v.status.toString))
      val expected = List((2, "Broken"),(1, "New"))
      assert(versions == expected)
    }

    "fail when instance wasn't created: unhandled error (State.New)" in {
      val cloudActor  = TestProbe()
      val nodeActor   = system.actorOf(NodeActor.props(nodesTable, cloudActor.ref))

      // Idle: send create command
      nodeActor ! Command.Create(newNode)
      expectMsgPF(5.seconds) {
        case Reply.CreateSuccess(_) =>
      }

      // New: create instance
      cloudActor.expectMsgPF(5.seconds) {
        case CloudActor.Command.Create(_, _) =>
      }

      // New: reply failed
      cloudActor.reply(new RuntimeException("noop"))

      // Shutdown: state must be Shutdown
      nodeActor ! Command.Status
      expectMsgPF(5.seconds) {
        case Reply.StatusSuccess(State.Shutdown, Data.Error(_)) =>
      }

      // Check persisted versions
      val versions = nodesTable.allVersionsFor(userId, role) map(v => (v.version, v.status.toString))
      val expected = List((2, "Broken"),(1, "New"))
      assert(versions == expected)
    }

    "fail when await instance is running: instance down (State.Pending)" in {
      val cloudActor  = TestProbe()
      val nodeActor   = system.actorOf(NodeActor.props(nodesTable, cloudActor.ref))

      // Idle: send create command
      nodeActor ! Command.Create(newNode)
      expectMsgPF(5.seconds) {
        case Reply.CreateSuccess(_) =>
      }

      // New: create instance
      cloudActor.expectMsgPF(5.seconds) {
        case CloudActor.Command.Create(_, _) =>
      }

      // New: instance created
      cloudActor.reply(CloudActor.Reply.CreateSuccess(pendingInstance))

      // Pending: await instance
      cloudActor.expectMsgPF(10.seconds) {
        case CloudActor.Command.Get(_) =>
      }

      // Pending: instance is down
      cloudActor.reply(CloudActor.Reply.CreateSuccess(brokenInstance))

      // Shutdown: state must be Shutdown
      nodeActor ! Command.Status
      expectMsgPF(5.seconds) {
        case Reply.StatusSuccess(State.Shutdown, Data.Error(_)) =>
      }

      // Check persisted versions
      val versions = nodesTable.allVersionsFor(userId, role) map(v => (v.version, v.status.toString))
      val expected = List((3, "Broken"),(2,"Pending"),(1, "New"))
      assert(versions == expected)
    }

    "fail when await instance is running: unhandled error (State.Pending)" in {
      val cloudActor  = TestProbe()
      val nodeActor   = system.actorOf(NodeActor.props(nodesTable, cloudActor.ref))

      // Idle: send create command
      nodeActor ! Command.Create(newNode)
      expectMsgPF(5.seconds) {
        case Reply.CreateSuccess(_) =>
      }

      // New: create instance
      cloudActor.expectMsgPF(5.seconds) {
        case CloudActor.Command.Create(_, _) =>
      }

      // New: instance created
      cloudActor.reply(CloudActor.Reply.CreateSuccess(pendingInstance))

      // Pending: await instance
      cloudActor.expectMsgPF(10.seconds) {
        case CloudActor.Command.Get(_) =>
      }

      // Pending: instance is down
      cloudActor.reply(new RuntimeException("noop"))

      // Shutdown: state must be Shutdown
      nodeActor ! Command.Status
      expectMsgPF(5.seconds) {
        case Reply.StatusSuccess(State.Shutdown, Data.Error(_)) =>
      }

      // Check persisted versions
      val versions = nodesTable.allVersionsFor(userId, role) map(v => (v.version, v.status.toString))
      val expected = List((3, "Broken"),(2,"Pending"),(1, "New"))
      assert(versions == expected)
    }
  }
}