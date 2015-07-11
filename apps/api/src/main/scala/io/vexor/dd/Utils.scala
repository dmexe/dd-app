package io.vexor.dd

import java.util.concurrent.TimeUnit

import akka.util.Timeout

object Utils {
  implicit class StringSquish(s: String) {
    def squish = s.replaceAll("\n", " ").replaceAll(" +", " ").trim
  }

  implicit class Tap[A](any: A) {
    def tap(f: (A) => Unit): A = {
      f(any)
      any
    }
  }

  def timeoutSec(n: Int) = {
    Timeout(n, TimeUnit.SECONDS)
  }
}

