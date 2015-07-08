package io.vexor.dd.models

import com.datastax.driver.core.{Cluster,QueryLogger,Session}
import io.vexor.dd.Utils
import Utils._

object Connector {
  var cluster: Option[Cluster] = None
  var session: Option[Session] = None
  val url:     Option[String]  = None

  def open(urlString: String) {
    close()

    val u = new java.net.URI(urlString)

    val host     = Option(u.getHost) filterNot(_.isEmpty) getOrElse("localhost")
    val keySpace = Option(u.getPath) filterNot(_.isEmpty) map(_.drop(1)) getOrElse("dd_api")
    val port     = Option(u.getPort) filterNot(_ == -1  ) getOrElse(9042)

    val cluster  = Cluster.builder().addContactPoint(host).withPort(port).build()
    val logger   = QueryLogger.builder(cluster).build()
    cluster.register(logger)

    val session  = cluster.connect()

    val sql = Seq(
      s"""
      CREATE KEYSPACE IF NOT EXISTS ${keySpace}
      WITH replication =
      {'class': 'SimpleStrategy', 'replication_factor' : 1}
      """.squish,
      s"""
      USE ${keySpace}
      """.squish
    )
    sql.foreach(session.execute)

    this.cluster = Some(cluster)
    this.session = Some(session)
  }

  def close() {
    cluster.foreach(_.close())
  }
}