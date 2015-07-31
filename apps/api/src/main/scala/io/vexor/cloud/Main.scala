package io.vexor.cloud

import akka.pattern.ask
import io.vexor.cloud.actors.MainActor
import scala.concurrent.Await


object Main extends App with AppEnv {
  implicit val timeout = Utils.timeoutSec(5)

  def shutdown(code: Int): Unit = {
    system.awaitTermination()
    System.exit(code)
  }

  val mainActor = system.actorOf(MainActor.props(appConfig), "main")
  val fu = mainActor ? MainActor.Command.Start

  Await.result(fu, timeout.duration).asInstanceOf[MainActor.StartReply] match {
    case MainActor.StartSuccess =>
      system.log.info("Successfuly started")
    case MainActor.StartFailure(e) =>
      system.log.error(s"Fail to boot app: $e")
      system.shutdown()
      shutdown(1)
  }

  shutdown(0)
}
