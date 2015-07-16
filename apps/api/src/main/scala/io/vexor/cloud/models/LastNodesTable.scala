package io.vexor.cloud.models

import java.util.{UUID,Date}
import scala.collection.JavaConversions._
import io.vexor.cloud.Utils.StringSquish
import com.datastax.driver.core.Row
import io.vexor.cloud.models.NodesTable.Status
import io.vexor.cloud.models.NodesTable.Status.Conversions.{ToInt,ToValue}

import scala.collection.JavaConverters._

class LastNodesTable(db:DB.Session, tableName:String) {

  import LastNodesTable._

  def up() {
    val sql = Seq(
      s"""
        CREATE TABLE IF NOT EXISTS $tableName (
          user_id    UUID,
          role       text,
          version    int,
          status     int,
          updated_at timestamp,
          PRIMARY KEY((user_id, role))
        )
        """.squish,
      s"CREATE INDEX IF NOT EXISTS ${tableName}_status_idx ON $tableName (status)"
    )
    sql.map(db.execute)
  }

  def down() {
    db.execute(s"DROP TABLE IF EXISTS $tableName")
  }

  def truncate(): Unit = {
    db.execute(s"TRUNCATE $tableName")
  }

  def fromRow(row: Row): Persisted = {
    val userId    = row.getUUID("user_id")
    val role      = row.getString("role")
    val version   = row.getInt("version")
    val status    = row.getInt("status")
    val updatedAt = row.getDate("updated_at")
    Persisted(userId, role, version, status.toValue, updatedAt)
  }

  def one(userId:UUID, role:String): Option[Persisted] = {
    val re = db.execute(
      s"SELECT * FROM $tableName WHERE user_id=? AND role=? LIMIT 1",
      userId,
      role
    ).one()
    Option(re) map fromRow
  }

  def allByStatus(statuses: List[Status.Value]): List[Persisted] = {
    statuses flatMap { s: Status.Value =>
      db.execute(s"SELECT * FROM $tableName WHERE status = ?", s.toInt)
        .all()
        .map(fromRow)
    }
  }

  def save(rec:NodesTable.Persisted): Option[Persisted] = {
    db.execute(
      s"INSERT INTO $tableName (user_id, role, version, status, updated_at) VALUES(?,?,?,?,?)",
      rec.userId,
      rec.role,
      rec.version : Integer,
      rec.status.toInt,
      rec.createdAt
    )
    one(rec.userId, rec.role)
  }
}

object LastNodesTable {
  val TABLE_NAME = "last_nodes"
  case class Persisted(userId:UUID, role:String, version:Int, status:Status.Value, updatedAt:Date)
  def apply(db: DB.Session) = new LastNodesTable(db, TABLE_NAME)
}
