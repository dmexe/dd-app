package io.vexor.docker.api

import akka.pattern.ask
import io.vexor.docker.api.actors.MainActor
import scala.concurrent.Await
import akka.util.Timeout
import scala.concurrent.duration.DurationInt
import scala.util.{Try,Success,Failure}

object Main extends App with AppEnv {

  implicit val timeout = Timeout(1.minute)

  def boot(): Try[Boolean] = {
    val mainActor = system.actorOf(MainActor.props(appConfig), "main")
    val fu = mainActor ? MainActor.Command.Start

    Try {
      Await.result(fu, timeout.duration).asInstanceOf[MainActor.StartReply] match {
        case MainActor.StartSuccess =>
          true
        case MainActor.StartFailure(e) =>
          throw new RuntimeException(s"Boot failed: $e")
      }
    }
  }

  boot() match {
    case Success(_) =>
      system.log.info("Successfuly started")
      system.awaitTermination()
    case Failure(e) =>
      system.log.error(s"Fail to boot app: $e")
      system.shutdown()
      system.awaitTermination()
      System.exit(1)
  }
}
