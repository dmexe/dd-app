package io.vexor.dd.actors

import java.util.UUID

import akka.actor._
import io.vexor.dd.cloud.{BaseCloudProvider}

object AttachCloudInstance {

  case class AttachTo(id: UUID)
  case class Attached(id: UUID)

  def props(conn: BaseCloudProvider) : Props = Props(new Coordinator(conn))
  def workerProps(conn: BaseCloudProvider) : Props = Props(new Worker(conn))

  class Worker(conn: BaseCloudProvider) extends Actor with ActorLogging {
    def receive = {
      case AttachTo(id) =>
        log.info(s"AttachTo ${id}")
        conn.create(id)
        sender() ! Attached(id)
    }
  }

  class Coordinator(conn: BaseCloudProvider) extends Actor with ActorLogging {

    def getOrCreateWorker(id: UUID): ActorRef = {
      val name = s"worker-${id}"
      context.child(name) getOrElse {
        val child = context.actorOf(workerProps(conn), name)
        context.watch(child)
        child
      }
    }

    def attach(m: AttachTo): Unit = {
      val child = getOrCreateWorker(m.id)
      child forward m
    }

    def receive = {
      case m: AttachTo =>
        log.info(s"Attach to ${m.id}")
        attach(m)
      case Terminated(child) =>
        log.info(s"Child terminated actor=${child}")
    }
  }

}
