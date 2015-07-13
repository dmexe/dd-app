package io.vexor.dd.models

import java.util.UUID

import io.vexor.dd.TestAppEnv
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import io.vexor.dd.models.NodesTable.Status

class NodesTableSpec extends WordSpecLike with Matchers with BeforeAndAfterAll with TestAppEnv {
  val userId  = new UUID(0,0)
  val _db     = new DB(dbUrl)
  val db      = NodesTable(_db.open().get)

  override def beforeAll() : Unit = {
    db.down()
    db.up()
  }

  override def afterAll() : Unit = {
    db.down()
    _db.close()
  }

  "A NodeTable" must {
    "find all new nodes" in {
      val role = "new-nodes"
      val n0 = NodesTable.New(userId, role)

      val n1 = db.save(n0).get
      assert(Seq(n1) == db.allNew())

      val n2 = db.save(n1, status = Status.Active).get
      assert(db.allNew().isEmpty)

      val n3 = db.save(n2, status = Status.New).get
      assert(Seq(n3) == db.allNew())
    }

    "successfuly create and update records" in {
      val role   = "default"
      val nRec = NodesTable.New(userId, role)

      var re  = db.save(nRec)
      re match {
        case Some(NodesTable.Persisted(pUserId, pRole, pVersion, pStatus, pCloudId, _)) =>
          assert(pUserId  == userId)
          assert(pRole    == role)
          assert(pVersion == 1)
          assert(pStatus  == Status.New)
          assert(pCloudId.isEmpty)
        case unknown =>
          fail(s"unknown $unknown")
      }
      val pRec  = re.get
      val pLast = db.last(userId, role).get
      assert(pRec == pLast)

      re = db.save(pRec, status = Status.Pending)
      re match {
        case Some(NodesTable.Persisted(pUserId, pRole, pVersion, pStatus, pCloudId, _)) =>
          assert(pUserId  == userId)
          assert(pRole    == role)
          assert(pVersion == 2)
          assert(pStatus  == Status.Pending)
          assert(pCloudId.isEmpty)
        case unknown =>
          fail(s"unknown $unknown")
      }
      val ppRec = re.get
      val ppLast = db.last(userId, role).get
      assert(ppRec == ppLast)

      re = db.save(ppRec, cloudId = "cloudId")
      re match {
        case Some(NodesTable.Persisted(pUserId, pRole, pVersion, pStatus, pCloudId, _)) =>
          assert(pUserId  == userId)
          assert(pRole    == role)
          assert(pVersion == 3)
          assert(pStatus  == Status.Pending)
          assert(pCloudId.get == "cloudId")
        case unknown =>
          fail(s"unknown $unknown")
      }
      val pppRec = re.get
      val pppLast = db.last(userId, role).get
      assert(pppRec == pppLast)

      val nnRec = NodesTable.New(userId, role)
      re  = db.save(nnRec)
      re match {
        case Some(NodesTable.Persisted(pUserId, pRole, pVersion, pStatus, pCloudId, _)) =>
          assert(pUserId  == userId)
          assert(pRole    == role)
          assert(pVersion == 4)
          assert(pStatus  == Status.New)
          assert(pCloudId.isEmpty)
        case unknown =>
          fail(s"unknown $unknown")
      }
      val ppppRec  = re.get
      val ppppLast = db.last(userId, role).get
      assert(ppppRec == ppppLast)
    }
  }
}

