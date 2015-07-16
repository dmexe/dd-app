package io.vexor.cloud

import java.util.concurrent.TimeUnit

import akka.util.Timeout

import scala.util.{Failure, Success, Try}

object Utils {
  implicit class StringSquish(s: String) {
    def squish = s.replaceAll("\n", " ").replaceAll(" +", " ").trim
  }

  implicit class TryToEither[A](obj: Try[A]) {
    def toEither : Either[Throwable, A] = {
      obj match {
        case Success(some) => Right(some)
        case Failure(e)    => Left(e)
      }
    }
  }

  implicit class OptionToTry[A](obj: Option[A]) {
    def toTry(e: Throwable): Try[A] = {
      obj match {
        case Some(v) => Success(v)
        case None    => Failure(e)
      }
    }
  }

  def timeoutSec(n: Int) = {
    Timeout(n, TimeUnit.SECONDS)
  }
}

