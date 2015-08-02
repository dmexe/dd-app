package io.vexor.docker.api.models

import java.util.UUID

import com.datastax.driver.core.{Row, Session}
import io.vexor.docker.api.Utils.StringSquish

import scala.util.Try

class CertsTable(db: Session, tableName: String) extends  {
  import CertsTable._

  def up(): Try[Boolean] = {
    val sql = Seq(
      s"""
        CREATE TABLE IF NOT EXISTS $tableName (
          user_id UUID,
          role    text,
          cert    text,
          key     text,
          PRIMARY KEY((user_id, role))
        )
      """.squish
    )
    Try {
      sql.map(db.execute)
      true
    }
  }

  def down() {
    db.execute(s"DROP TABLE IF EXISTS $tableName")
  }

  def truncate(): Unit = {
    db.execute(s"TRUNCATE $tableName")
  }

  private def fromRow(row: Row): Record = {
    val userId = row.getUUID("user_id")
    val role   = row.getString("role")
    val cert   = row.getString("cert")
    val key    = row.getString("key")
    Record(userId, role, cert, key)
  }

  def save(rec: Record): Record = {
    db.execute(
      s"INSERT INTO $tableName (user_id, role, cert, key) VALUES (?, ?, ?, ?)",
      rec.userId,
      rec.role,
      rec.cert,
      rec.key
    )
    rec
  }

  def one(userId: UUID, role: String): Option[Record] = {
    val row = db.execute(s"SELECT * FROM $tableName WHERE user_id=? AND role=?", userId, role).one()
    Option(row) map fromRow
  }
}

object CertsTable extends {

  val TABLE_NAME = "certs"

  case class Record(userId: UUID, role: String, cert: String, key: String)
}
