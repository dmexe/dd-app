package io.vexor.docker.api.models

import com.datastax.driver.core.{Row, Session}

class PropertiesTable(val session: Session, val tableName: String) extends QueryBuilder {
  import PropertiesTable._

  def up() {
    s"""
      CREATE TABLE IF NOT EXISTS $tableName (
        name    text,
        value   text,
        PRIMARY KEY(name)
      )
    """.execute()
  }

  private def fromRow(row: Row): Record = {
    val name    = row.getString("name")
    val value   = row.getString("value")
    Record(name, value)
  }

  def save(rec: Record): Record = {
    insertInto()
      .value("name",  rec.name)
      .value("value", rec.value)
      .execute()
    rec
  }

  def one(name: String): Option[Record] = {
    selectFrom().where("name".qEq(name)).one(fromRow)
  }
}

object PropertiesTable extends {

  val TABLE_NAME = "properties"

  case class Record(name: String, value: String)
}
