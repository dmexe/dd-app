package io.vexor.dd

import akka.actor.ActorSystem
import akka.actor.Actor
import akka.actor.Props
import akka.testkit.{ TestActors, TestKit, TestKitBase, ImplicitSender }
import org.scalatest.WordSpecLike
import org.scalatest.Matchers
import org.scalatest.BeforeAndAfterAll
 
class PingPongActorSpec extends TestKitBase with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with ApplicationEnv {

  override def appEnv   = "test"
  override def afterAll = system.shutdown()

  "A Ping actor" must {
    "send back a ping on a pong" in {
      val pingActor = system.actorOf(PingActor.props)
      pingActor ! PongActor.PongMessage("pong")
      expectMsg(PingActor.PingMessage("ping"))
    }
  }

  "A Pong actor" must {
    "send back a pong on a ping" in {
      val pongActor = system.actorOf(PongActor.props)
      pongActor ! PingActor.PingMessage("ping")
      expectMsg(PongActor.PongMessage("pong"))
    }
  }

}
