package io.vexor.docker.api.actors

import java.util.UUID

import akka.actor.{Actor, ActorLogging, Props}
import io.vexor.docker.api.models.{KeyGen, CA, CertsTable}
import io.vexor.docker.api.models.CertsTable.Record

class CertsActor(db: CertsTable, clientsCa: CA) extends Actor with ActorLogging {
  import CertsActor._

  def createCert(userId: UUID, role: String): Record = {
    val subject    = s"${userId}.${role}"
    val clientCert = KeyGen.toPEM(KeyGen.genCert(clientsCa.re, subject))
    val rec        = Record(userId, role, clientCert.cert, clientCert.privateKey)
    db.save(rec)
    rec
  }

  def receive = {
    case Command.Get(userId, role) =>
      val rec   = db.one(userId, role) getOrElse createCert(userId, role)
      val reply = GetSuccess(clientsCa.certPem, rec.cert, rec.key)
      sender() ! reply
  }
}

object CertsActor {

  object Command {
    case class Get(userId: UUID, role: String)
  }

  sealed trait GetReply
  case class GetSuccess(ca: String, cert: String, key: String) extends GetReply

  def props(db: CertsTable, clientsCa: CA): Props = Props(new CertsActor(db, clientsCa))
}
