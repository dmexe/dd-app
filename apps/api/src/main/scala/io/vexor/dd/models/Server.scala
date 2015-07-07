package io.vexor.dd.models

import java.util.{Date, UUID}

import com.datastax.driver.core.Row
import io.vexor.dd.Utils
import Utils._

object Server {

  val tableName = "servers"
  lazy val conn = Connector.session.get

  object Status extends Enumeration {
    val New, Pending, Active, Frozen, Finished, Broken = Value

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

  abstract class Record

  case class NewRecord(
    role:      String
  ) extends Record

  case class PersistedRecord (
    id:        UUID,
    role:      String,
    status:    Status.Value,
    updatedAt: Date
  ) extends Record

  object Schema {

    def up() {
      val sql = Seq(
        s"""
        CREATE TABLE IF NOT EXISTS ${tableName} (
          id         TimeUUID Primary Key,
          role       text,
          status     int,
          updated_at timestamp
        )
        """.squish,
        s"""
        CREATE INDEX IF NOT EXISTS ${tableName}_role ON ${tableName} (role)
        """.squish
      )
      sql.map(conn.execute)
    }

    def down() {
      conn.execute(s"DROP TABLE IF EXISTS ${tableName}")
    }
  }

  object Table {

    import Status._

    def fromRow(row: Row): PersistedRecord = {
      PersistedRecord(
        row.getUUID("id"),
        row.getString("role"),
        row.getInt("status").toValue,
        row.getDate("updated_at")
      )
    }

    def save(server: NewRecord): Unit = {
      val sql =
        s"""
        INSERT INTO ${tableName} (id, role, status, updated_at)
        VALUES (now(), ?, 0, dateof(now()))
        """.squish
      conn.execute(sql, server.role)
    }

    def save(server: PersistedRecord): Unit = {
      val sql =
        s"""
        UPDATE ${tableName} SET
          role = ?,
          status = ?,
          updated_at = dateof(now())
        WHERE id = ?
        """.squish
      conn.execute(sql, server.role, server.status.toInt, server.id)
    }

    def oneByRole(role: String): Option[PersistedRecord] = {
      val sql =
        s"""
        SELECT * FROM ${tableName} WHERE role = ? LIMIT 1
        """.squish
      val re = conn.execute(sql, role).one()
      Option(re).map(fromRow)
    }
  }

}
