package io.vexor.dd.models

import java.util.{Date, UUID}

import com.datastax.driver.core.{Row, Session}
import io.vexor.dd.Utils._

class NodesTable(session: Session) extends  {

  import NodesTable.Status.Conversions.{ToInt, ToValue}
  import NodesTable._

  def up() {
    val sql = Seq(
      s"""
        CREATE TABLE IF NOT EXISTS $tableName (
          user_id    UUID,
          role       text,
          version    int,
          status     int,
          cloud_id   text,
          created_at timestamp,
          PRIMARY KEY(user_id, role, version)
        ) WITH CLUSTERING ORDER BY (role ASC, version DESC)
        """.squish
    )
    sql.map(session.execute)
  }

  def down() {
    session.execute(s"DROP TABLE IF EXISTS $tableName")
  }

  def fromRow(row: Row): Persisted = {
    val userId    = row.getUUID("user_id")
    val role      = row.getString("role")
    val version   = row.getInt("version")
    val status    = row.getInt("status")
    val cloudId   = row.getString("cloud_id")
    val createdAt = row.getDate("created_at")
    Persisted(userId, role, version, status.toValue, Option(cloudId), createdAt)
  }

  def nextVersion(r: Record): Int = {
    val sql = s"SELECT version FROM ${tableName} WHERE user_id=? AND role=? ${orderBy} LIMIT 1"
    val ver = session.execute(sql, r.userId, r.role).one().getInt("version")
    Option(ver).getOrElse(0) + 1
  }

  def save(rec: New): Option[Persisted] = {
    val version = nextVersion(rec)

    session.execute(
      s"INSERT INTO $tableName (user_id, role, version, status, created_at) VALUES (?, ?, ?, 0, dateOf(now()))",
      rec.userId,
      rec.role,
      version: Integer
    )

    val re = session.execute(
      s"SELECT * FROM ${tableName} WHERE user_id=? AND role=? AND version=?",
      rec.userId,
      rec.role,
      version: Integer
    ).one()

    Option(re).map(fromRow)
  }

  def save(prev: Persisted, status: Status.Value = Status._Undefined, cloudId: String = ""): Option[Persisted] = {
    val version = prev.version + 1

    val newStatus =
      if(status == Status._Undefined) {
        prev.status
      } else {
        status
      }

    val newCloudId =
      if(cloudId == "") {
        prev.cloudId
      } else {
        cloudId
      }

    session.execute(
      s"INSERT INTO $tableName (user_id, role, version, status, cloudId, created_at) VALUES (?, ?, ?, 0, dateOf(now()))",
      prev.userId,
      prev.role,
      version: Integer,
      newStatus.toInt,
      newCloudId
    )

    val re = session.execute(
      s"SELECT * FROM ${tableName} WHERE user_id=? AND role=? AND version=?",
      prev.userId,
      prev.role,
      version: Integer
    ).one()

    Option(re).map(fromRow)
  }

  def last(userId: UUID, role: String): Option[Persisted] = {
    val rec = session.execute(
      s"SELECT * FROM ${tableName} WHERE user_id=? AND role=? ${orderBy} LIMIT 1",
      userId,
      role
    ).one()
    Option(rec).map(fromRow)
  }
}

object NodesTable extends {

  val tableName = "nodes"
  val orderBy   = "ORDER BY role ASC, version DESC"

  object Status extends Enumeration {
    val New, Pending, Active, Frozen, Finished, Broken, _Undefined = Value

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
    new NodesTable(session)
  }
}
