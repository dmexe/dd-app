package io.vexor.docker.api.models

import com.datastax.driver.core.{Cluster, QueryLogger}
import io.vexor.docker.api.Utils._

object DB {
  type Session = com.datastax.driver.core.Session
}

class DB (url: String) {

  import DB._

  var session = Option.empty[Session]

  def open(): Session = {
    close()
    val re = openSession(url)
    session = Some(re)
    re
  }

  def isOpen : Boolean = {
    session.isDefined
  }

  def close(): Unit = {
    session.foreach { s =>
      s.close()
      s.getCluster.close()
    }
    session = None
  }

  private def openSession(urlString: String): Session = {
    val u = new java.net.URI(urlString)

    val host     = Option(u.getHost) filterNot(_.isEmpty) getOrElse "localhost"
    val keySpace = Option(u.getPath) filterNot(_.isEmpty) map(_.drop(1)) getOrElse "dd_api"
    val port     = Option(u.getPort) filterNot(_ == -1  ) getOrElse 9042

    val cluster  = Cluster.builder().addContactPoint(host).withPort(port).build()

    try {
      val logger = QueryLogger.builder(cluster).build()
      cluster.register(logger)

      val session  = cluster.connect()

      val sql = Seq(
        s"""
        CREATE KEYSPACE IF NOT EXISTS $keySpace
        WITH replication =
        {'class': 'SimpleStrategy', 'replication_factor' : 1}
        """.squish,
        s"""
        USE $keySpace
        """.squish
      )
      sql.foreach(session.execute)
      session
    } catch {
      case e: Throwable =>
        cluster.close()
        throw e
    }
  }

}

