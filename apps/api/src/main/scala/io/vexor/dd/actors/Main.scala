package io.vexor.dd.actors

import akka.actor.{Props, ActorLogging, Actor}
import io.vexor.dd.{AppEnv}

object Main {
  case class Init()
  case class Initialized()
  def props(env: AppEnv) : Props = Props(new Main(env))
}

class Main(env: AppEnv) extends Actor with ActorLogging {

  import Main._

  def receive = {
    case Init =>
      val dbActor = context.actorOf(DB.props, "db")
      dbActor ! DB.Open(env.dbUrl)
      sender() ! Initialized
    case DB.Ok(db) =>
      val getReadyServerActor = context.actorOf(GetReadyServer.props(db), "get-ready-server")
    case unknown =>
      log.info(s"Receive unknown ${unknown}")
  }
}

