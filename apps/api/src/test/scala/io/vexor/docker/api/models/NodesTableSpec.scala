package io.vexor.docker.api.models

import java.util.UUID

import io.vexor.docker.api.TestAppEnv
import io.vexor.docker.api.TestAppEnv
import io.vexor.docker.api.models.NodesTable.Status
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import io.vexor.docker.api.models.NodesTable.Status

class NodesTableSpec extends WordSpecLike with Matchers with BeforeAndAfterAll with TestAppEnv {
  val userId  = new UUID(0,0)
  val reg     = ModelRegistry(dbUrl, "NodesTableSpec")
  val db      = reg.nodes

  override def beforeAll() : Unit = {
    db.down()
    db.up()
  }

  override def afterAll() : Unit = {
    db.down()
    reg.db.close()
  }

  "A NodeTable" must {
    "find all running nodes" in {
      val n0 = NodesTable.New(userId, "n")
      val a0 = NodesTable.New(userId, "a")

      val a1 = db.save(a0)
      val n1 = db.save(n0)
      assert(List(n1, a1) == db.allRunning())

      val n2 = db.save(n1, status = Status.Pending)
      assert(List(a1, n2) == db.allRunning())

      val n3 = db.save(n2, status = Status.Active)
      assert(List(a1, n3) == db.allRunning())

      val n4 = db.save(n3, status = Status.Finished)
      assert(List(a1) == db.allRunning())

      val n5 = db.save(n4, status = Status.Broken)
      assert(List(a1) == db.allRunning())
    }

    "successfuly create and update records" in {
      val role   = "default"
      val nRec = NodesTable.New(userId, role)

      var rec  = db.save(nRec)

      assert(rec.userId  == userId)
      assert(rec.role    == role)
      assert(rec.version == 1)
      assert(rec.status  == Status.New)
      assert(rec.cloudId.isEmpty)

      val pLast = db.last(userId, role).get
      assert(rec == pLast)

      rec = db.save(rec, status = Status.Pending)
      assert(rec.userId  == userId)
      assert(rec.role    == role)
      assert(rec.version == 2)
      assert(rec.status  == Status.Pending)
      assert(rec.cloudId.isEmpty)

      val ppLast = db.last(userId, role).get
      assert(rec == ppLast)

      rec = db.save(rec, cloudId = Some("cloudId"))
      assert(rec.userId  == userId)
      assert(rec.role    == role)
      assert(rec.version == 3)
      assert(rec.status  == Status.Pending)
      assert(rec.cloudId.get == "cloudId")

      val pppLast = db.last(userId, role).get
      assert(rec == pppLast)

      val nnRec = NodesTable.New(userId, role)
      rec = db.save(nnRec)
      assert(rec.userId  == userId)
      assert(rec.role    == role)
      assert(rec.version == 4)
      assert(rec.status  == Status.New)
      assert(rec.cloudId.isEmpty)

      val ppppLast = db.last(userId, role).get
      assert(rec == ppppLast)
    }
  }
}

