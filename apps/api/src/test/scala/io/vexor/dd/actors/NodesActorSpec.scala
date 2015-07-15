package io.vexor.dd.actors

import akka.testkit.{ImplicitSender, TestKitBase}
import io.vexor.dd.TestAppEnv
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class NodesActorSpec extends TestKitBase with ImplicitSender
with WordSpecLike with Matchers with BeforeAndAfterAll with TestAppEnv {

}