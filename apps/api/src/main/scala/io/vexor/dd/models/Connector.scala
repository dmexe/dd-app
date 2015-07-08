package io.vexor.dd.models

import com.datastax.driver.core.{Cluster, QueryLogger, Session}
import io.vexor.dd.Utils._

object Connector {
  def apply(urlString: String) : Session = {
    val u = new java.net.URI(urlString)

    val host     = Option(u.getHost) filterNot(_.isEmpty) getOrElse "localhost"
    val keySpace = Option(u.getPath) filterNot(_.isEmpty) map(_.drop(1)) getOrElse "dd_api"
    val port     = Option(u.getPort) filterNot(_ == -1  ) getOrElse 9042

    val cluster  = Cluster.builder().addContactPoint(host).withPort(port).build()
    val logger   = QueryLogger.builder(cluster).build()
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
  }

  def close(sess: Session): Unit = {
    sess.getCluster.close()
  }
}

trait Connector {
  def session : Session
}