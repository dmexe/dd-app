package io.vexor.cloud

import akka.util.Timeout
import scala.concurrent.duration.DurationInt

trait DefaultTimeout {
  implicit val timeout = Timeout(5.seconds)
}
