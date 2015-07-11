package io.vexor.dd.models

import java.util.{Date, UUID}

import com.datastax.driver.core.{Row, Session}
import io.vexor.dd.Utils._

class Server(session: Session) extends  {

  import Server.Status.Conversions._
  import Server.{New, Persisted, tableName}

  def up() {
    val sql = Seq(
      s"""
        CREATE TABLE IF NOT EXISTS $tableName (
          id         TimeUUID Primary Key,
          role       text,
          status     int,
          updated_at timestamp
        )
        """.squish,
      s"""
        CREATE INDEX IF NOT EXISTS ${tableName}_role ON $tableName (role)
        """.squish
    )
    sql.map(session.execute)
  }

  def down() {
    session.execute(s"DROP TABLE IF EXISTS $tableName")
  }

  def fromRow(row: Row): Persisted = {
    Persisted(
      row.getUUID("id"),
      row.getString("role"),
      row.getInt("status").toValue,
      row.getDate("updated_at")
    )
  }

  def save(server: New): Unit = {
    val sql =
      s"""
      INSERT INTO $tableName (id, role, status, updated_at)
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

object Server extends {

  val tableName = "servers"

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
    role:      String,
    status:    Status.Value,
    updatedAt: Date
  ) extends Record

  def apply(session: Session): Server = {
    new Server(session)
  }
}
