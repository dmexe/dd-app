package io.vexor.docker.api.actors

import java.time.Instant
import java.util.UUID

import akka.actor.{ActorRef, Props}
import akka.testkit._
import io.vexor.docker.api.TestAppEnv
import io.vexor.docker.api.actors.NodeActor.{Command, Data, State}
import io.vexor.docker.api.cloud.{AbstractCloud, TestCloud}
import io.vexor.docker.api.models.{ModelRegistry, NodesTable}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Matchers, WordSpecLike}

import scala.concurrent.duration.{DurationInt,FiniteDuration}

class NodeActorSpec extends TestKitBase with ImplicitSender
with WordSpecLike with Matchers with BeforeAndAfterAll with BeforeAndAfterEach with TestAppEnv {

  val userId     = new UUID(0,0)
  val reg        = ModelRegistry(dbUrl, "NodeActorSpec")
  val db         = reg.nodes
  val instanceId = "0"
  val role       = "node-actor-spec"
  val newNode    = NodesTable.New(userId, role)

  val pendingInstance  = TestCloud.Instance(instanceId, "name", "127.0.0.1", userId, role, 1, AbstractCloud.Status.Pending, Instant.now())
  val activeInstance   = pendingInstance.copy(status = AbstractCloud.Status.On)
  val inactiveInstance = pendingInstance.copy(status = AbstractCloud.Status.Off)
  val brokenInstance   = pendingInstance.copy(status = AbstractCloud.Status.Broken)

  override def beforeAll() = {
    db.down()
    db.up()
  }

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
    db.down()
    reg.db.close()
  }

  override def beforeEach() = {
    db.truncate()
  }

  def passIdleState(nodeActor: ActorRef): Unit = {
    // Idle: state must be Idle
    nodeActor ! Command.Status
    expectMsg(NodeActor.StatusSuccess(State.Idle, Data.Empty))

    // Idle: send create
    nodeActor ! Command.Create
    expectMsgPF(5.seconds) {
      case NodeActor.CreateSuccess(node) =>
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
    cloudActor.reply(CloudActor.CreateSuccess(pendingInstance))
  }

  def passPendingState(nodeActor: ActorRef, cloudActor: TestProbe): Unit = {
    // Pending: state must be Pending
    nodeActor ! Command.Status
    expectMsgPF(5.seconds) {
      case NodeActor.StatusSuccess(State.Pending, Data.Node(node)) =>
        assert(node.status == NodesTable.Status.Pending)
    }

    // Pending: await instance
    for (i <- 0 to 2) {
      cloudActor.expectMsgPF(10.seconds) {
        case CloudActor.Command.Get(id) =>
          assert(id == instanceId)
      }
      if (i == 2) {
        cloudActor.reply(CloudActor.GetSuccess(activeInstance))
      } else {
        cloudActor.reply(CloudActor.GetSuccess(pendingInstance))
      }
    }
  }

  def passActiveState(nodeActor: ActorRef, cloudActor: TestProbe): Unit = {
    // Active: state must be Active
    nodeActor ! Command.Status
    expectMsgPF(5.seconds) {
      case NodeActor.StatusSuccess(State.Active, Data.Node(node)) =>
        assert(node.status == NodesTable.Status.Active)
    }

    // Active: await instance termination
    for (i <- 0 to 2) {
      cloudActor.expectMsgPF(10.seconds) {
        case CloudActor.Command.Get(id) =>
          assert(id == instanceId)
      }
      if (i == 2) {
        cloudActor.reply(CloudActor.GetSuccess(inactiveInstance))
      } else {
        cloudActor.reply(CloudActor.GetSuccess(activeInstance))
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
      case NodeActor.StatusSuccess(State.Active, Data.Node(node)) =>
        assert(node.status == NodesTable.Status.Active)
    }

    cloudActor.expectMsgPF(10.seconds) {
      case CloudActor.Command.Get(id) =>
        assert(id == instanceId)
    }

    cloudActor.reply(m)
  }

  def assertPersistentVersions(expected: List[(Int, String)]): Unit = {
    val versions = db.allVersionsFor(userId, role) map(v => (v.version, v.status.toString))
    assert(versions == expected)
  }


  def getNodeActor(db: NodesTable, cloud: ActorRef): ActorRef = {
    val inst = Props(new NodeActor(db, cloud, userId, role) {
      override lazy val tickInterval   = 300.millis
    })
    val re = system.actorOf(inst)
    Thread.sleep(100) // for recovery
    re
  }

  def expectIdleState(nodeActor: ActorRef) = {
    nodeActor ! Command.Status
    expectMsg(NodeActor.StatusSuccess(State.Idle, Data.Empty))
  }

  def expectActiveState(nodeActor: ActorRef) = {
    nodeActor ! Command.Status
    expectMsgPF(5.seconds) {
      case NodeActor.StatusSuccess(State.Active, Data.Node(_)) =>
    }
  }

  def expectPendingState(nodeActor: ActorRef) = {
    nodeActor ! Command.Status
    expectMsgPF(5.seconds) {
      case NodeActor.StatusSuccess(State.Pending, Data.Node(_)) =>
    }
  }

  def expectCloudGet(cloudActor: TestProbe) = {
    cloudActor.expectMsgPF(10.seconds) {
      case CloudActor.Command.Get(_) =>
    }
  }

  def cloudReplySuccess(cloudActor: TestProbe, instance: CloudActor.Instance) = {
    cloudActor.reply(CloudActor.GetSuccess(instance))
  }

  "A NodeActor actor" must {
    "successfuly create and processing node" in {
      val cloudActor = TestProbe()
      val nodeActor  = getNodeActor(db, cloudActor.ref)

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

    "successfuly wait for missing instance" in {
      val cloudActor = TestProbe()
      val nodeActor  = getNodeActor(db, cloudActor.ref)

      expectIdleState(nodeActor)
      passIdleState(nodeActor)
      passNewState(cloudActor)
      expectPendingState(nodeActor)

      // send pending
      expectCloudGet(cloudActor)
      cloudActor.reply(CloudActor.GetSuccess(pendingInstance))
      expectPendingState(nodeActor)

      // send failure
      expectCloudGet(cloudActor)
      cloudActor.reply(CloudActor.GetFailure("noop"))
      expectPendingState(nodeActor)

      // send active
      expectCloudGet(cloudActor)
      cloudActor.reply(CloudActor.GetSuccess(activeInstance))
      expectActiveState(nodeActor)
    }

    "successfuly process get instance command" in {
      val cloudActor = TestProbe()
      val nodeActor  = getNodeActor(db, cloudActor.ref)

      expectIdleState(nodeActor)

      nodeActor ! NodeActor.Command.GetInstance
      expectMsgPF(5.seconds) {
        case NodeActor.GetInstanceFailure(error) =>
      }

      passIdleState(nodeActor)
      passNewState(cloudActor)
      passPendingState(nodeActor, cloudActor)

      // pending -> fail
      nodeActor ! NodeActor.Command.GetInstance
      cloudActor.expectMsgPF(5.seconds) {
        case CloudActor.Command.Get(id) =>
      }
      cloudActor.reply(CloudActor.GetSuccess(pendingInstance))
      expectMsgPF(5.seconds) {
        case NodeActor.GetInstanceFailure(error) =>
      }

      // active -> succ
      nodeActor ! NodeActor.Command.GetInstance
      cloudActor.expectMsgPF(5.seconds) {
        case CloudActor.Command.Get(id) =>
      }
      cloudActor.reply(CloudActor.GetSuccess(activeInstance))
      expectMsgPF(5.seconds) {
        case NodeActor.GetInstanceSuccess(instance) =>
          assert(instance == activeInstance)
      }

      // fail -> fail
      nodeActor ! NodeActor.Command.GetInstance
      cloudActor.expectMsgPF(5.seconds) {
        case CloudActor.Command.Get(id) =>
      }
      cloudActor.reply(CloudActor.GetFailure("noop"))
      expectMsgPF(5.seconds) {
        case NodeActor.GetInstanceFailure(error) =>
      }
    }

    "fail when instance wasn't created: error in cloud provider (State.New)" in {
      val cloudActor  = TestProbe()
      val nodeActor  = getNodeActor(db, cloudActor.ref)

      passIdleState(nodeActor)
      failNewStateWith(cloudActor, CloudActor.CreateFailure("noop"))
      expectIdleState(nodeActor)

      val expected = List((2, "Broken"),(1, "New"))
      assertPersistentVersions(expected)
    }

    "fail when instance wasn't created: unhandled error (State.New)" in {
      val cloudActor  = TestProbe()
      val nodeActor  = getNodeActor(db, cloudActor.ref)

      passIdleState(nodeActor)
      failNewStateWith(cloudActor, "noop")
      expectIdleState(nodeActor)

      val expected = List((2, "Broken"),(1, "New"))
      assertPersistentVersions(expected)
    }

    "fail when await instance is running: instance down (State.Pending)" in {
      val cloudActor  = TestProbe()
      val nodeActor  = getNodeActor(db, cloudActor.ref)

      passIdleState(nodeActor)
      passNewState(cloudActor)
      failPendingWith(cloudActor, CloudActor.GetSuccess(brokenInstance))
      expectIdleState(nodeActor)

      val expected = List((3, "Broken"),(2,"Pending"),(1, "New"))
      assertPersistentVersions(expected)
    }

    "success when await instance is running: unhandled error (State.Pending)" in {
      val cloudActor = TestProbe()
      val nodeActor  = getNodeActor(db, cloudActor.ref)

      passIdleState(nodeActor)
      passNewState(cloudActor)
      failPendingWith(cloudActor, "noop")

      nodeActor ! Command.Status
      expectMsgPF(5.seconds) {
        case NodeActor.StatusSuccess(State.Pending, _) =>
      }

      val expected = List((2,"Pending"),(1, "New"))
      assertPersistentVersions(expected)
    }

    "fail await instance is running: state timeout reached (State.Pending)" in {
      val cloudActor = TestProbe()
      val nodeActorProps = Props(new NodeActor(db, cloudActor.ref, userId, role) {
        override lazy val tickInterval   = 100.millis
        override lazy val pendingTimeout = 150.millis
      })
      val nodeActor = system.actorOf(nodeActorProps)

      Thread.sleep(100) // for recovery

      passIdleState(nodeActor)
      passNewState(cloudActor)

      cloudActor.expectMsgPF(5.seconds) {
        case CloudActor.Command.Get(id) =>
      }
      cloudActor.reply(CloudActor.GetSuccess(pendingInstance))

      Thread.sleep(300)
      expectIdleState(nodeActor)

      val expected = List((3, "Broken"), (2, "Pending"),(1, "New"))
      assertPersistentVersions(expected)
    }


    "fail when await instance termination: instance down (State.Active)" in {
      val cloudActor  = TestProbe()
      val nodeActor  = getNodeActor(db, cloudActor.ref)

      passIdleState(nodeActor)
      passNewState(cloudActor)
      passPendingState(nodeActor, cloudActor)
      failActiveWith(nodeActor, cloudActor, CloudActor.GetSuccess(brokenInstance))
      expectIdleState(nodeActor)

      val expected = List((4, "Broken"),(3, "Active"),(2,"Pending"),(1, "New"))
      assertPersistentVersions(expected)
    }

    "fail when await instance termination: unhandled error (State.Active)" in {
      val cloudActor  = TestProbe()
      val nodeActor  = getNodeActor(db, cloudActor.ref)

      passIdleState(nodeActor)
      passNewState(cloudActor)
      passPendingState(nodeActor, cloudActor)
      failActiveWith(nodeActor, cloudActor, "noop")
      expectIdleState(nodeActor)

      val expected = List((4, "Broken"),(3, "Active"),(2,"Pending"),(1, "New"))
      assertPersistentVersions(expected)
    }

    "successfuly recovery actor state from New" in {
      val node       = db.save(newNode)
      val cloudActor = TestProbe()
      val nodeActor  = getNodeActor(db, cloudActor.ref)

      passNewState(cloudActor)

      nodeActor ! NodeActor.Command.Status
      expectMsgPF(5.seconds) {
        case NodeActor.StatusSuccess(NodeActor.State.Pending, _) =>
      }
      val expected = List((2,"Pending"),(1, "New"))
      assertPersistentVersions(expected)
    }

    "successfuly recovery actor state from Pending" in {
      val node       = db.save(newNode)
      db.save(node, status = NodesTable.Status.Pending)
      val cloudActor = TestProbe()
      val nodeActor  = getNodeActor(db, cloudActor.ref)

      nodeActor ! NodeActor.Command.Status
      expectMsgPF(5.seconds) {
        case NodeActor.StatusSuccess(NodeActor.State.Pending, _) =>
      }
    }

    "successfuly recovery actor state from Active" in {
      val node       = db.save(newNode)
      db.save(node, status = NodesTable.Status.Active)
      val cloudActor = TestProbe()
      val nodeActor  = getNodeActor(db, cloudActor.ref)

      nodeActor ! NodeActor.Command.Status
      expectMsgPF(5.seconds) {
        case NodeActor.StatusSuccess(NodeActor.State.Active, _) =>
      }
    }

    "successfuly recovery actor state from Finished" in {
      val node       = db.save(newNode)
      db.save(node, status = NodesTable.Status.Finished)
      val cloudActor = TestProbe()
      val nodeActor  = getNodeActor(db, cloudActor.ref)

      nodeActor ! NodeActor.Command.Status
      expectMsgPF(5.seconds) {
        case NodeActor.StatusSuccess(NodeActor.State.Idle, _) =>
      }
    }

    "successfuly recovery actor state from Broken" in {
      val node       = db.save(newNode)
      db.save(node, status = NodesTable.Status.Broken)
      val cloudActor = TestProbe()
      val nodeActor  = getNodeActor(db, cloudActor.ref)

      nodeActor ! NodeActor.Command.Status
      expectMsgPF(5.seconds) {
        case NodeActor.StatusSuccess(NodeActor.State.Idle, _) =>
      }
    }
  }
}

