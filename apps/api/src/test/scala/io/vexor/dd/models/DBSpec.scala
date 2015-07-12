package io.vexor.dd.models

import io.vexor.dd.TestAppEnv
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.util.{Failure, Success}

class DBSpec extends WordSpecLike with Matchers with BeforeAndAfterAll with TestAppEnv {

  "A DB" must {
    "successfully open and close session" in {
      val db = new DB(dbUrl)
      db.open() match {
        case Success(s) =>
        case unknown    => fail(unknown.toString)
      }

      assert(db.isOpen)

      db.close()
    }

    "fail to open session" in {
      val db = new DB("bad.url")
      db.open() match {
        case Failure(e) =>
        case unknown    => fail(unknown.toString)
      }

      assert(db.isOpen == false)
    }
  }
}