package io.vexor.docker.api.models

class ModelRegistry(val db: DB, val session: DB.Session, suffix: String) {
  lazy val nodes      = new NodesTable(session, s"${NodesTable.TABLE_NAME}$suffix")
  lazy val properties = new PropertiesTable(session, s"${PropertiesTable.TABLE_NAME}$suffix")
  lazy val certs      = new CertsTable(session, s"${CertsTable.TABLE_NAME}$suffix")

  def up() : ModelRegistry = {
    nodes.up()
    properties.up()
    certs.up()
    this
  }
}

object ModelRegistry {
  def apply(url: String, suffix: String = ""): ModelRegistry = {
    val db      = new DB(url)
    val session = db.open()
    new ModelRegistry(db, session, suffix)
  }
}
