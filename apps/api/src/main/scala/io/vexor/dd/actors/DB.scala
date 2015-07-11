package io.vexor.dd.actors

import akka.actor.{Props, ActorLogging, Actor}
import com.datastax.driver.core.{QueryLogger, Cluster}
import io.vexor.dd.Utils._

object DB {
  type Session = com.datastax.driver.core.Session

  case class Open(url: String)
  case class Ready(db: Session)
  case class Close()
  case class Closed()

  def props : Props = Props(new DB)
}

class DB extends Actor with ActorLogging {

  import DB._

  var session = Option.empty[Session]

  def open(urlString: String) : Session = {
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

  def close(): Unit = {
    session.foreach(_.getCluster.close())
    session = None
  }

  def receive = {
    case Open(url) =>
      sender() ! Ready(open(url))
    case Close =>
      close()
      sender() ! Closed
  }
}

