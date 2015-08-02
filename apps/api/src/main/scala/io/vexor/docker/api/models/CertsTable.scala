package io.vexor.docker.api.models

import java.util.UUID
import com.datastax.driver.core.{Row, Session}

class CertsTable(val session: Session, val tableName: String)  extends QueryBuilder {
  import CertsTable._

  def up() {
    s"""
      CREATE TABLE IF NOT EXISTS $tableName (
        user_id UUID,
        role    text,
        cert    text,
        key     text,
        PRIMARY KEY((user_id, role))
      )
    """.execute()
  }

  private def fromRow(row: Row): Record = {
    val userId = row.getUUID("user_id")
    val role   = row.getString("role")
    val cert   = row.getString("cert")
    val key    = row.getString("key")
    Record(userId, role, cert, key)
  }

  def save(rec: Record): Record = {
    insertInto()
      .value("user_id", rec.userId)
      .value("role",    rec.role)
      .value("cert",    rec.cert)
      .value("key",     rec.key)
      .execute()
    rec
  }

  def one(userId: UUID, role: String): Option[Record] = {
    selectFrom()
      .where("user_id".qEq(userId)).and("role".qEq(role))
      .one(fromRow)
  }
}

object CertsTable extends {

  val TABLE_NAME = "certs"

  case class Record(userId: UUID, role: String, cert: String, key: String)
}
