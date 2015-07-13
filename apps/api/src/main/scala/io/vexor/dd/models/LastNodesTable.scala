package io.vexor.dd.models

import java.util.{UUID,Date}
import io.vexor.dd.Utils.StringSquish
import com.datastax.driver.core.{Row}
import io.vexor.dd.models.NodesTable.Status
import io.vexor.dd.models.NodesTable.Status.Conversions.{ToInt,ToValue}

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
        """.squish
    )
    sql.map(db.execute)
  }

  def down() {
    db.execute(s"DROP TABLE IF EXISTS $tableName")
  }

  def fromRow(row: Row): Persisted = {
    val userId    = row.getUUID("user_id")
    val role      = row.getString("role")
    val version   = row.getInt("version")
    val status    = row.getInt("status")
    val updatedAt = row.getDate("updated_at")
    Persisted(userId, role, version, status.toValue, updatedAt)
  }

  def findOne(userId:UUID, role:String): Option[Persisted] = {
    val re = db.execute(
      s"SELECT * FROM $tableName WHERE user_id=? AND role=? LIMIT 1",
      userId,
      role
    ).one()
    Option(re) map fromRow
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
    findOne(rec.userId, rec.role)
  }
}

object LastNodesTable {
  val TABLE_NAME = "last_nodes"
  case class Persisted(userId:UUID, role:String, version:Int, status:Status.Value, updatedAt:Date)
  def apply(db: DB.Session) = new LastNodesTable(db, TABLE_NAME)
}
