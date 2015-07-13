package io.vexor.dd.actors


import akka.actor.{ActorRef, Props, ActorLogging, Actor}
import io.vexor.dd.models.NodesTable
import scala.concurrent.duration.DurationInt

class WatchNewNodesActor(nodesActor: ActorRef) extends Actor with ActorLogging {

  import context.dispatcher
  import WatchNewNodesActor._

  type NodesList = List[NodesTable.Persisted]

  var nodes: NodesList = List.empty
  val tick  = context.system.scheduler.schedule(0.seconds, 10.seconds, self, Tick())

  override def postStop() = tick.cancel()

  def tickAction(): Unit = {
    nodesActor ! NodesActor.NewNodes()
  }

  def diff(newNodes: NodesList): NodesList = {
    val difference = newNodes.diff(nodes)
    difference
  }

  def receive = {
    case Tick() =>
      tickAction()
    case NodesActor.NewNodesSuccess(newNodes) =>
      diff(newNodes)
  }
}

object WatchNewNodesActor {
  def props(nodesActor: ActorRef): Props =
    Props(new WatchNewNodesActor(nodesActor))

  case class Tick()
}
