package io.vexor.docker.api.models

import java.util.{Date, UUID}

import scala.collection.JavaConversions._

import com.datastax.driver.core.{Row, Session}
import io.vexor.docker.api.Utils.StringSquish

import scala.util.Try

class NodesTable(db: Session, tableName: String) extends  {

  import NodesTable._
  import NodesTable.Status.Conversions.{ToInt, ToValue}

  val lastTableName    = s"last_$tableName"
  val versionTableName = s"version_$tableName"

  def up(): Try[Boolean] = {
    val sql = Seq(
      s"""
        CREATE TABLE IF NOT EXISTS $versionTableName (
          user_id    UUID,
          role       text,
          version    int,
          status     int,
          cloud_id   text,
          created_at timestamp,
          PRIMARY KEY((user_id, role), version)
        ) WITH CLUSTERING ORDER BY (version DESC)
        """.squish,
      s"""
        CREATE TABLE IF NOT EXISTS $lastTableName (
          user_id    UUID,
          role       text,
          version    int,
          status     int,
          cloud_id   text,
          created_at timestamp,
          PRIMARY KEY((user_id, role))
        )
        """.squish,
      s"CREATE INDEX IF NOT EXISTS ${lastTableName}_on_status_idx ON $lastTableName (status)"
    )
    Try {
      sql.map(db.execute)
      true
    }
  }

  def down() {
    db.execute(s"DROP TABLE IF EXISTS $versionTableName")
    db.execute(s"DROP TABLE IF EXISTS $lastTableName")
  }

  def truncate(): Unit = {
    db.execute(s"TRUNCATE $versionTableName")
    db.execute(s"TRUNCATE $lastTableName")
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

  private def oneInLastNodes(userId: UUID, role: String): Option[Persisted] = {
    val re = db.execute(
      s"SELECT * FROM $versionTableName WHERE user_id=? AND role=? LIMIT 1",
      userId,
      role
    ).one()
    Option(re) map fromRow
  }

  private def saveInLastNodes(rec:Persisted): Option[Persisted] = {
    db.execute(
      s"INSERT INTO $lastTableName (user_id, role, version, status, cloud_id, created_at) VALUES(?,?,?,?,?,?)",
      rec.userId,
      rec.role,
      rec.version : Integer,
      rec.status.toInt,
      rec.cloudId.orNull,
      rec.createdAt
    )
    oneInLastNodes(rec.userId, rec.role)
  }

  private def allByStatus(statuses: List[Status.Value]): List[Persisted] = {
    statuses flatMap { s: Status.Value =>
      db.execute(s"SELECT * FROM $lastTableName WHERE status = ?", s.toInt)
        .all()
        .map(fromRow)
    }
  }

  def nextVersion(r: Record): Int = {
    val sql = s"SELECT version FROM $versionTableName WHERE user_id=? AND role=? $ORDER_BY LIMIT 1"
    val ver = db.execute(sql, r.userId, r.role).one()
    Option(ver).map(_.getInt("version")).getOrElse(0) + 1
  }

  def save(rec: New): Option[Persisted] = {
    val version = nextVersion(rec)

    db.execute(
      s"INSERT INTO $versionTableName (user_id, role, version, status, created_at) VALUES (?, ?, ?, 0, dateOf(now()))",
      rec.userId,
      rec.role,
      version: Integer
    )

    for {
      maybeRec  <- one(rec.userId, rec.role, version)
      maybeLast <- saveInLastNodes(maybeRec)
    } yield maybeRec
  }

  def save(prev: Persisted, status: Status.Value = Status.Undefined, cloudId: Option[String] = None): Option[Persisted] = {
    val version = prev.version + 1

    val newStatus =
      if(status == Status.Undefined) {
        prev.status
      } else {
        status
      }

    val newCloudId = cloudId orElse prev.cloudId

    db.execute(
      s"INSERT INTO $versionTableName (user_id, role, version, status, cloud_id, created_at) VALUES (?, ?, ?, ?, ?, dateOf(now()))",
      prev.userId,
      prev.role,
      version: Integer,
      newStatus.toInt,
      newCloudId.orNull
    )

    for {
      maybeRec  <- one(prev.userId, prev.role, version)
      maybeLast <- saveInLastNodes(maybeRec)
    } yield maybeRec
  }

  def one(userId: UUID, role: String, version: Int): Option[Persisted] = {
    val row = db.execute(
      s"SELECT * FROM $versionTableName WHERE user_id=? AND role=? AND version=?",
      userId,
      role,
      version: Integer
    ).one()
    Option(row) map fromRow
  }

  def one(userId: UUID, role: String): Option[Persisted] = {
    val row = db.execute(
      s"SELECT * FROM $versionTableName WHERE user_id=? AND role=? $ORDER_BY LIMIT 1",
      userId,
      role
    ).one()
    Option(row) map fromRow
  }

  def last(userId: UUID, role: String): Option[Persisted] = {
    oneInLastNodes(userId, role)
  }

  def allVersionsFor(userId: UUID, role: String): List[Persisted] = {
    val re = db.execute(
      s"SELECT * FROM $versionTableName WHERE user_id=? AND role=? $ORDER_BY",
      userId,
      role
    ).all()
    re.toList map fromRow
  }

  def allRunning() : List[Persisted] = {
    allByStatus(List(Status.New, Status.Pending, Status.Active))
  }
}

object NodesTable extends {

  val TABLE_NAME = "nodes"
  val ORDER_BY   = "ORDER BY version DESC"

  object Status extends Enumeration {
    val New, Pending, Active, Finished, Broken, Undefined = Value

    object Conversions {
      implicit class ToInt(v : Value) {
        def toInt : Integer = v match {
          case New      => 0
          case Pending  => 1
          case Active   => 2
          case Finished => 3
          case Broken   => 4
        }
      }

      implicit class ToValue(i : Int) {
        def toValue : Value = i match {
          case 0 => New
          case 1 => Pending
          case 2 => Active
          case 3 => Finished
          case 4 => Broken
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
}
