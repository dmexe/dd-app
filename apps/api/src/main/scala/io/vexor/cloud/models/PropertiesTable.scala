package io.vexor.cloud.models

import com.datastax.driver.core.{Row, Session}
import io.vexor.cloud.Utils.StringSquish

class PropertiesTable(db: Session, tableName: String) extends  {
  import PropertiesTable._

  def up() {
    val sql = Seq(
      s"""
        CREATE TABLE IF NOT EXISTS $tableName (
          name    text,
          value   text,
          PRIMARY KEY(name)
        )
      """.squish
    )
    sql.map(db.execute)
  }

  def down() {
    db.execute(s"DROP TABLE IF EXISTS $tableName")
  }

  def truncate(): Unit = {
    db.execute(s"TRUNCATE $tableName")
  }

  private def fromRow(row: Row): Record = {
    val name    = row.getString("name")
    val value   = row.getString("value")
    Record(name, value)
  }

  def save(rec: Record): Option[Record] = {
    db.execute(
      s"INSERT INTO $tableName (name, value) VALUES (?, ?)",
      rec.name,
      rec.value
    )
    one(rec.name)
  }

  def one(name: String): Option[Record] = {
    val row = db.execute(s"SELECT * FROM $tableName WHERE name=?", name).one()
    Option(row) map fromRow
  }
}

object PropertiesTable extends {

  val TABLE_NAME = "properties"

  case class Record(name: String, value: String)

  def apply(session: Session): PropertiesTable = {
    new PropertiesTable(session, tableName = TABLE_NAME)
  }
}
