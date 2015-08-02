package io.vexor.docker.api.models

import com.datastax.driver.core.querybuilder.{Insert, QueryBuilder => QB, Select}
import com.datastax.driver.core.{Session,Row}
import io.vexor.docker.api.Utils.StringSquish
import scala.collection.JavaConversions._

trait QueryBuilder {

  val tableName: String
  val session:   Session

  def insertInto() = QB.insertInto(tableName)
  def selectFrom() = QB.select().from(tableName)
  def selectColumn(s: String) = QB.select().column(s).from(tableName)

  def down():Unit     = s"DROP TABLE IF EXISTS $tableName".execute()
  def truncate():Unit = s"TRUNCATE $tableName".execute()

  implicit class StringSession(s: String) {
    def execute()        = session.execute(s.squish)
    def qEq(any: Object) = QB.eq(s, any)
    def qAsc()           = QB.asc(s)
    def qDesc()          = QB.desc(s)
  }

  implicit class InsertSession(i: Insert) {
    def execute() = session.execute(i)
  }

  implicit class SelectWhereSession(s: Select.Where) {
    def one() = Option(session.execute(s).one())
    def one[T](fn: Row => T) = Option(session.execute(s).one()) map fn
    def all() = session.execute(s).all()
    def all[T](fn: Row => T) = session.execute(s).all().toList map fn
  }
}