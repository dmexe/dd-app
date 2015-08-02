package io.vexor.docker.api.models

import java.time.Instant
import java.util.{UUID,Date}


import com.datastax.driver.core.{Row, Session}

class NodesTable(val session: Session, val tableName: String) extends QueryBuilder {

  import NodesTable._
  import NodesTable.Status.Conversions.{ToInt, ToValue}

  val verTable  = new VersionTable(session, s"version_$tableName")
  val lastTable = new LastTable(session, s"last_$tableName")
  val orderBy   = "version".qDesc()

  def up(): Unit = {
    verTable.up()
    lastTable.up()
  }

  override def down(): Unit = {
    verTable.down()
    lastTable.down()
  }

  override def truncate(): Unit = {
    verTable.truncate()
    lastTable.truncate()
  }

  private def fromRow(row: Row): Persisted = {
    val userId    = row.getUUID("user_id")
    val role      = row.getString("role")
    val version   = row.getInt("version")
    val status    = row.getInt("status")
    val cloudId   = row.getString("cloud_id")
    val createdAt = row.getDate("created_at").toInstant
    Persisted(userId, role, version, status.toValue, Option(cloudId), createdAt)
  }

  private def allByStatus(statuses: List[Status.Value]): List[Persisted] = {
    statuses flatMap { s: Status.Value =>
      lastTable.selectFrom()
        .where("status".qEq(s.toInt))
        .all(fromRow)
    }
  }

  def nextVersion(rec: Record): Int = {
    val found =
      verTable.selectColumn("version")
        .orderBy(orderBy)
        .where("user_id".qEq(rec.userId))
        .and("role".qEq(rec.role))
        .one map(_.getInt("version")) getOrElse 0
    found + 1
  }

  def save(newRec: New): Persisted = {
    val version   = nextVersion(newRec)
    val createdAt = Instant.now()
    val status    = Status.New
    val rec       = Persisted(newRec.userId, newRec.role, version, status, None, createdAt)

    verTable.insert(rec)
    lastTable.insert(rec)
    rec
  }

  def save(prev: Persisted, status: Status.Value = Status.Undefined, cloudId: Option[String] = None): Persisted = {
    val version = prev.version + 1

    val newStatus =
      if(status == Status.Undefined) {
        prev.status
      } else {
        status
      }

    val newCloudId = cloudId orElse prev.cloudId
    val createdAt  = Instant.now()
    val newRec     = Persisted(prev.userId, prev.role, version, newStatus, newCloudId, createdAt)

    verTable.insert(newRec)
    lastTable.insert(newRec)

    newRec
  }

  def one(userId: UUID, role: String, version: Integer): Option[Persisted] = {
    verTable.selectFrom()
      .orderBy(orderBy)
      .where("user_id".qEq(userId)).and("role".qEq(role)).and("version".qEq(version))
      .one(fromRow)
  }

  def one(userId: UUID, role: String): Option[Persisted] = {
    verTable.selectFrom()
      .orderBy(orderBy)
      .where("user_id".qEq(userId)).and("role".qEq(role))
      .one(fromRow)
  }

  def last(userId: UUID, role: String): Option[Persisted] = {
    lastTable.selectFrom()
      .where("user_id".qEq(userId)).and("role".qEq(role))
      .one(fromRow)
  }

  def allVersionsFor(userId: UUID, role: String): List[Persisted] = {
    verTable.selectFrom()
      .orderBy(orderBy)
      .where("user_id".qEq(userId)).and("role".qEq(role))
      .all(fromRow)
  }

  def allRunning() : List[Persisted] = {
    allByStatus(List(Status.New, Status.Pending, Status.Active))
  }
}

object NodesTable extends {

  val TABLE_NAME = "nodes"

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
    createdAt: Instant
  ) extends Record

  trait Inserter extends QueryBuilder {

    import NodesTable.Status.Conversions.ToInt

    def insert(rec: Persisted) = {
      insertInto()
        .value("user_id",    rec.userId)
        .value("role",       rec.role)
        .value("version",    rec.version)
        .value("status",     rec.status.toInt)
        .value("cloud_id",   rec.cloudId.orNull)
        .value("created_at", Date.from(rec.createdAt))
        .execute()
    }
  }

  class VersionTable(val session: Session, val tableName: String) extends Inserter {
    def up() = {
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
      """.execute()
    }
  }

  class LastTable(val session: Session, val tableName: String) extends Inserter {
    def up(): Unit = {
      s"""
        CREATE TABLE IF NOT EXISTS $tableName (
          user_id    UUID,
          role       text,
          version    int,
          status     int,
          cloud_id   text,
          created_at timestamp,
          PRIMARY KEY((user_id, role))
        )
      """.execute()
      s"""
        CREATE INDEX IF NOT EXISTS ${tableName}_on_status_idx ON $tableName (status)
      """.execute()
    }
  }
}
