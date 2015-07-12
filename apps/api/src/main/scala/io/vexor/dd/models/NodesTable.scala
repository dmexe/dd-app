package io.vexor.dd.models

import java.util.{Date, UUID}

import com.datastax.driver.core.{Row, Session}
import io.vexor.dd.Utils._

class NodesTable(session: Session) extends  {

  import NodesTable.Status.Conversions._
  import NodesTable.{New, Persisted, tableName}

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
    val id        = row.getUUID("id")
    val parentId  = row.getUUID("parent_id")
    val cloudId   = row.getString("cloud_id")
    val role      = row.getString("role")
    val status    = row.getInt("status")
    val createdAt = row.getDate("created_at")
    Persisted(id, Option(parentId), Option(cloudId), role, status.toValue, createdAt)
  }

  def save(server: New): Unit = {
    val sql =
      s"""
      INSERT INTO $tableName (id, role, status, created_at)
      VALUES (now(), ?, 0, dateof(now()))
      """.squish
    session.execute(sql, server.role)
  }

  def save(server: Persisted): Unit = {
    val sql =
      s"""
      UPDATE $tableName SET
        role = ?,
        status = ?,
        updated_at = dateof(now())
      WHERE id = ?
      """.squish
    session.execute(sql, server.role, server.status.toInt, server.id)
  }

  def oneByRole(role: String): Option[Persisted] = {
    val sql =
      s"""
      SELECT * FROM $tableName WHERE role = ? LIMIT 1
      """.squish
    val re = session.execute(sql, role).one()
    Option(re).map(fromRow)
  }
}

object NodesTable extends {

  val tableName = "nodes"

  object Status extends Enumeration {
    val New, Pending, Active, Frozen, Finished, Broken = Value

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

  abstract class Record

  case class New(
    role:      String
  ) extends Record

  case class Persisted (
    id:        UUID,
    parentId:  Option[UUID],
    cloudId:   Option[String],
    role:      String,
    status:    Status.Value,
    createdAt: Date
  ) extends Record

  def apply(session: Session): NodesTable = {
    new NodesTable(session)
  }
}
