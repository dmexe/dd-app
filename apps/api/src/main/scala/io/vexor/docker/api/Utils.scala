package io.vexor.docker.api

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

    def toTry(s: String): Try[A] = {
      obj.toTry(new RuntimeException(s))
    }
  }
}

