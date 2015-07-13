package io.vexor.dd.models

import java.util.{Date, UUID}

import scala.collection.JavaConversions._

import com.datastax.driver.core.{Row, Session}
import io.vexor.dd.Utils.StringSquish

class NodesTable(db: Session, tableName: String) extends  {

  import NodesTable._
  import NodesTable.Status.Conversions.{ToInt, ToValue}

  val lastNodes = new LastNodesTable(db, s"last_$tableName")

  def up() {
    lastNodes.up()
    val sql = Seq(
      s"""
        CREATE TABLE IF NOT EXISTS $tableName (
          user_id    UUID,
          role       text,
          version    int,
          status     int,
          cloud_id   text,
          created_at timestamp,
          PRIMARY KEY((user_id, role), version)
        ) WITH CLUSTERING ORDER BY (version DESC)
        """.squish
    )
    sql.map(db.execute)
  }

  def down() {
    db.execute(s"DROP TABLE IF EXISTS $tableName")
    lastNodes.down()
  }

  private def fromRow(row: Row): Persisted = {
    val userId    = row.getUUID("user_id")
    val role      = row.getString("role")
    val version   = row.getInt("version")
    val status    = row.getInt("status")
    val cloudId   = row.getString("cloud_id")
    val createdAt = row.getDate("created_at")
    Persisted(userId, role, version, status.toValue, Option(cloudId), createdAt)
  }

  private def saveInLastNodes(rec:Persisted) = {
    lastNodes.save(rec)
  }

  def nextVersion(r: Record): Int = {
    val sql = s"SELECT version FROM $tableName WHERE user_id=? AND role=? $ORDER_BY LIMIT 1"
    val ver = db.execute(sql, r.userId, r.role).one()
    Option(ver).map(_.getInt("version")).getOrElse(0) + 1
  }

  def save(rec: New): Option[Persisted] = {
    val version = nextVersion(rec)

    db.execute(
      s"INSERT INTO $tableName (user_id, role, version, status, created_at) VALUES (?, ?, ?, 0, dateOf(now()))",
      rec.userId,
      rec.role,
      version: Integer
    )

    for {
      maybeRec  <- one(rec.userId, rec.role, version)
      maybeLast <- saveInLastNodes(maybeRec)
    } yield maybeRec
  }

  def save(prev: Persisted, status: Status.Value = Status.Undefined, cloudId: String = ""): Option[Persisted] = {
    val version = prev.version + 1

    val newStatus =
      if(status == Status.Undefined) {
        prev.status
      } else {
        status
      }

    val newCloudId =
      if(cloudId == "") {
        prev.cloudId.orNull
      } else {
        cloudId
      }

    db.execute(
      s"INSERT INTO $tableName (user_id, role, version, status, cloud_id, created_at) VALUES (?, ?, ?, ?, ?, dateOf(now()))",
      prev.userId,
      prev.role,
      version: Integer,
      newStatus.toInt,
      newCloudId
    )

    for {
      maybeRec  <- one(prev.userId, prev.role, version)
      maybeLast <- saveInLastNodes(maybeRec)
    } yield maybeRec
  }

  def one(userId: UUID, role: String, version: Int): Option[Persisted] = {
    val row = db.execute(
      s"SELECT * FROM $tableName WHERE user_id=? AND role=? AND version=?",
      userId,
      role,
      version: Integer
    ).one()
    Option(row) map fromRow
  }

  def one(userId: UUID, role: String): Option[Persisted] = {
    val row = db.execute(
      s"SELECT * FROM $tableName WHERE user_id=? AND role=? $ORDER_BY LIMIT 1",
      userId,
      role
    ).one()
    Option(row) map fromRow
  }

  def last(userId: UUID, role: String): Option[Persisted] = {
    for {
      lastRec <- lastNodes.one(userId, role)
      rec     <- one(userId, role, lastRec.version)
    } yield rec
  }

  def allVersionsFor(userId: UUID, role: String): List[Persisted] = {
    val re = db.execute(
      s"SELECT * FROM $tableName WHERE user_id=? AND role=? $ORDER_BY",
      userId,
      role
    ).all()
    re.toList map fromRow
  }

  def allNew() : List[Persisted] = {
    val re = lastNodes.allByStatus(Seq(Status.New))
    re flatMap { l =>
      one(l.userId, l.role, l.version)
    }
  }
}

object NodesTable extends {

  val TABLE_NAME = "nodes"
  val ORDER_BY   = "ORDER BY version DESC"

  object Status extends Enumeration {
    val New, Pending, Active, Frozen, Finished, Broken, Undefined = Value

    object Conversions {
      implicit class ToInt(v : Value) {
        def toInt : Integer = v match {
          case New      => 0
          case Pending  => 1
          case Active   => 2
          case Frozen   => 3
          case Finished => 4
          case Broken   => 5
        }
      }

      implicit class ToValue(i : Int) {
        def toValue : Value = i match {
          case 0 => New
          case 1 => Pending
          case 2 => Active
          case 3 => Frozen
          case 4 => Finished
          case 5 => Broken
        }
      }
    }
  }

  trait Record {
    val userId: UUID
    val role:   String
  }

  case class New(
    userId:    UUID,
    role:      String
  ) extends Record

  case class Persisted (
    userId:    UUID,
    role:      String,
    version:   Int,
    status:    Status.Value,
    cloudId:   Option[String],
    createdAt: Date
  ) extends Record

  def apply(session: Session): NodesTable = {
    new NodesTable(session, tableName = TABLE_NAME)
  }
}
