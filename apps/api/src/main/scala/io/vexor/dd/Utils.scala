package io.vexor.dd

object Utils {
  implicit class StringSquish(s: String) {
    def squish = s.replaceAll("\n", " ").replaceAll(" +", " ").trim
  }
}

