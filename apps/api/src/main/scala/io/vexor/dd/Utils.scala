package io.vexor.dd

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
}

