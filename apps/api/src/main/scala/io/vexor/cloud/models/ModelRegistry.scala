package io.vexor.cloud.models

import scala.util.Try

class ModelRegistry(val db: DB, val session: DB.Session, suffix: String) {
  lazy val nodes      = new NodesTable(session, s"${NodesTable.TABLE_NAME}$suffix")
  lazy val properties = new PropertiesTable(session, s"${PropertiesTable.TABLE_NAME}$suffix")

  def up() : Try[Boolean] = {
    for {
      _ <- nodes.up()
      _ <- properties.up()
    } yield true
  }
}

object ModelRegistry {
  def apply(url: String, suffix: String = ""): Try[ModelRegistry] = {
    val db           = new DB(url)
    val maybeSession = db.open()

    maybeSession.map { session =>
      new ModelRegistry(db, session, suffix)
    }
  }
}
