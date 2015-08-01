package io.vexor.docker.api

import akka.util.Timeout
import scala.concurrent.duration.DurationInt

trait DefaultTimeout {
  implicit val timeout = Timeout(5.seconds)
}
