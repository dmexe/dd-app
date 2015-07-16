package io.vexor.cloud.actors

import java.util.UUID

import akka.actor.{Props, ActorRef, ActorSystem}
import akka.testkit._
import io.vexor.cloud.TestAppEnv
import io.vexor.cloud.cloud.{TestCloud, AbstractCloud}
import io.vexor.cloud.models.{NodesTable, DB}
import org.scalatest.{BeforeAndAfterEach, BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.concurrent.duration.DurationInt
import io.vexor.cloud.actors.NodeActor.{Command,Reply,State,Data}

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
    TestKit.shutdownActorSystem(system)
    nodesTable.down()
  }

  override def beforeEach() = {
    nodesTable.truncate()
  }

  def passIdleState(nodeActor: ActorRef): Unit = {
    // Idle: state must be Idle
    nodeActor ! Command.Status
    expectMsg(Reply.StatusSuccess(State.Idle, Data.Empty))

    // Idle: send create
    nodeActor ! Command.Create(newNode)
    expectMsgPF(5.seconds) {
      case Reply.CreateSuccess(node) =>
        assert(node.status == NodesTable.Status.New)
    }
  }

  def passNewState(cloudActor: TestProbe): Unit = {
    // New: create instance
    cloudActor.expectMsgPF(5.seconds) {
      case CloudActor.Command.Create(i, r, v) =>
        assert(i == userId)
        assert(r == role)
    }

    // New: reply instance if pending
    cloudActor.reply(CloudActor.Reply.CreateSuccess(pendingInstance))
  }

  def passPendingState(nodeActor: ActorRef, cloudActor: TestProbe): Unit = {
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
  }

  def passActiveState(nodeActor: ActorRef, cloudActor: TestProbe): Unit = {
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
  }

  def failNewStateWith(cloudActor: TestProbe, m: Any): Unit = {
    // New: create instance
    cloudActor.expectMsgPF(5.seconds) {
      case CloudActor.Command.Create(_, _, _) =>
    }

    // New: reply instance if fail
    cloudActor.reply(m)
  }

  def failPendingWith(cloudActor: TestProbe, m: Any): Unit = {
    // Pending: await instance
    cloudActor.expectMsgPF(10.seconds) {
      case CloudActor.Command.Get(_) =>
    }

    // Pending: instance is down
    cloudActor.reply(m)
  }

  def failActiveWith(nodeActor: ActorRef, cloudActor: TestProbe, m: Any): Unit = {
    // Active: state must be Active
    nodeActor ! Command.Status
    expectMsgPF(5.seconds) {
      case Reply.StatusSuccess(State.Active, Data.Node(node)) =>
        assert(node.status == NodesTable.Status.Active)
    }

    cloudActor.expectMsgPF(10.seconds) {
      case CloudActor.Command.Get(id) =>
        assert(id == instanceId)
    }

    cloudActor.reply(m)
  }

  def assertPersistentVersions(expected: List[Tuple2[Int, String]]): Unit = {
    val versions = nodesTable.allVersionsFor(userId, role) map(v => (v.version, v.status.toString))
    assert(versions == expected)
  }


  def getNodeActor(db: NodesTable, cloud: ActorRef): ActorRef = {
    val inst = Props(new NodeActor(db, cloud) {
      override val tickInterval = 300.millis
    })
    system.actorOf(inst)
  }

  def expectIdleState(nodeActor: ActorRef) = {
    nodeActor ! Command.Status
    expectMsg(Reply.StatusSuccess(State.Idle, Data.Empty))
  }

  "A NodeActor actor" must {
    "successfuly create and processing node" in {
      val cloudActor = TestProbe()
      val nodeActor  = getNodeActor(nodesTable, cloudActor.ref)

      expectIdleState(nodeActor)
      passIdleState(nodeActor)
      passNewState(cloudActor)
      passPendingState(nodeActor, cloudActor)
      passActiveState(nodeActor, cloudActor)
      expectIdleState(nodeActor)

      // TODO: shutdown instance
      val expected = List((4,"Broken"), (3,"Active"), (2,"Pending"), (1,"New"))
      assertPersistentVersions(expected)
    }

    "fail when node wasn't created (State.Idle)" in {
      val cloudActor  = TestProbe()
      val fNodesTable = new NodesTable(db, tableName) {
        override def save(node: NodesTable.New): Option[NodesTable.Persisted] = None
      }
      val nodeActor  = getNodeActor(fNodesTable, cloudActor.ref)

      // Idle: send create and get fail
      nodeActor ! Command.Create(newNode)
      expectMsgPF(5.seconds) {
        case Reply.CreateFailure(error) =>
      }

      expectIdleState(nodeActor)

      assertPersistentVersions(List.empty)
    }

    "fail when instance wasn't created: error in cloud provider (State.New)" in {
      val cloudActor  = TestProbe()
      val nodeActor  = getNodeActor(nodesTable, cloudActor.ref)

      passIdleState(nodeActor)
      failNewStateWith(cloudActor, CloudActor.Reply.CreateFailure("noop"))
      expectIdleState(nodeActor)

      val expected = List((2, "Broken"),(1, "New"))
      assertPersistentVersions(expected)
    }

    "fail when instance wasn't created: unhandled error (State.New)" in {
      val cloudActor  = TestProbe()
      val nodeActor  = getNodeActor(nodesTable, cloudActor.ref)

      passIdleState(nodeActor)
      failNewStateWith(cloudActor, "noop")
      expectIdleState(nodeActor)

      val expected = List((2, "Broken"),(1, "New"))
      assertPersistentVersions(expected)
    }

    "fail when await instance is running: instance down (State.Pending)" in {
      val cloudActor  = TestProbe()
      val nodeActor  = getNodeActor(nodesTable, cloudActor.ref)

      passIdleState(nodeActor)
      passNewState(cloudActor)
      failPendingWith(cloudActor, CloudActor.Reply.CreateSuccess(brokenInstance))
      expectIdleState(nodeActor)

      val expected = List((3, "Broken"),(2,"Pending"),(1, "New"))
      assertPersistentVersions(expected)
    }

    "fail when await instance is running: unhandled error (State.Pending)" in {
      val cloudActor  = TestProbe()
      val nodeActor  = getNodeActor(nodesTable, cloudActor.ref)

      passIdleState(nodeActor)
      passNewState(cloudActor)
      failPendingWith(cloudActor, "noop")
      expectIdleState(nodeActor)

      val expected = List((3, "Broken"),(2,"Pending"),(1, "New"))
      assertPersistentVersions(expected)
    }

    "fail when await instance termination: instance down (State.Active)" in {
      val cloudActor  = TestProbe()
      val nodeActor  = getNodeActor(nodesTable, cloudActor.ref)

      passIdleState(nodeActor)
      passNewState(cloudActor)
      passPendingState(nodeActor, cloudActor)
      failActiveWith(nodeActor, cloudActor, CloudActor.Reply.GetSuccess(brokenInstance))
      expectIdleState(nodeActor)

      val expected = List((4, "Broken"),(3, "Active"),(2,"Pending"),(1, "New"))
      assertPersistentVersions(expected)
    }

    "fail when await instance termination: unhandled error (State.Active)" in {
      val cloudActor  = TestProbe()
      val nodeActor  = getNodeActor(nodesTable, cloudActor.ref)

      passIdleState(nodeActor)
      passNewState(cloudActor)
      passPendingState(nodeActor, cloudActor)
      failActiveWith(nodeActor, cloudActor, "noop")
      expectIdleState(nodeActor)

      val expected = List((4, "Broken"),(3, "Active"),(2,"Pending"),(1, "New"))
      assertPersistentVersions(expected)
    }

    "successfuly recovery actor state from New" in {
      val cloudActor = TestProbe()
      val nodeActor  = getNodeActor(nodesTable, cloudActor.ref)
      val node       = nodesTable.save(newNode).get

      nodeActor ! NodeActor.Command.Recovery(node)
      expectMsg(NodeActor.Reply.RecoverySuccess(NodeActor.State.New))
    }

    "successfuly recovery actor state from Pending" in {
      val cloudActor = TestProbe()
      val nodeActor  = getNodeActor(nodesTable, cloudActor.ref)
      var node       = nodesTable.save(newNode).get
      node = node.copy(status = NodesTable.Status.Pending)

      nodeActor ! NodeActor.Command.Recovery(node)
      expectMsg(NodeActor.Reply.RecoverySuccess(NodeActor.State.Pending))
    }

    "successfuly recovery actor state from Active" in {
      val cloudActor = TestProbe()
      val nodeActor  = getNodeActor(nodesTable, cloudActor.ref)
      var node       = nodesTable.save(newNode).get
      node = node.copy(status = NodesTable.Status.Active)

      nodeActor ! NodeActor.Command.Recovery(node)
      expectMsg(NodeActor.Reply.RecoverySuccess(NodeActor.State.Active))
    }

    "fail to recovery actor state from Finished" in {
      val cloudActor = TestProbe()
      val nodeActor  = getNodeActor(nodesTable, cloudActor.ref)
      var node       = nodesTable.save(newNode).get
      node = node.copy(status = NodesTable.Status.Finished)

      nodeActor ! NodeActor.Command.Recovery(node)
      expectMsgPF(5.seconds) {
        case NodeActor.Reply.RecoveryFailure(_, _) =>
      }

      expectIdleState(nodeActor)
    }

    "fail to recovery actor state from Broken" in {
      val cloudActor = TestProbe()
      val nodeActor  = getNodeActor(nodesTable, cloudActor.ref)
      var node       = nodesTable.save(newNode).get
      node = node.copy(status = NodesTable.Status.Broken)

      nodeActor ! NodeActor.Command.Recovery(node)
      expectMsgPF(5.seconds) {
        case NodeActor.Reply.RecoveryFailure(_, _) =>
      }

      expectIdleState(nodeActor)
    }
  }
}

