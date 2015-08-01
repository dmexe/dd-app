package io.vexor.cloud.models

import java.io.ByteArrayOutputStream
import com.jcraft.jsch.{JSch, KeyPair}

import scala.util.Try

object SshKey {

  val CP   = "US-ASCII"

  def apply(db: PropertiesTable, id: String): Try[SshKey] = {
    val pubKeyName  = s"$id.sshpub"
    val privKeyName = s"$id.sshpriv"
    Try {
      load(db, id, pubKeyName, privKeyName) getOrElse genAndSave(db, id, pubKeyName, privKeyName)
    }
  }

  private def load(db: PropertiesTable, id: String, pubKeyName: String, privKeyName: String): Option[SshKey] = {
    val keyPair =
      for {
        pub  <- db.one(pubKeyName)
        priv <- db.one(privKeyName)
      } yield KeyPair.load(null, priv.value.getBytes(CP), pub.value.getBytes(CP))
    val sshKey = keyPair map(keyPairToSshKey(id, _))
    sshKey
  }

  private def genAndSave(db: PropertiesTable, id: String, pubKeyName: String, privKeyName: String): SshKey = {
    val keyPair = KeyPair.genKeyPair(null, KeyPair.RSA, 2048)
    val sshKey  = keyPairToSshKey(id, keyPair)
    db.save(PropertiesTable.Record(privKeyName, sshKey.privateKey))
    db.save(PropertiesTable.Record(pubKeyName,  sshKey.publicKey))
    sshKey
  }

  private def keyPairToSshKey(comment: String, keyPair: KeyPair): SshKey = {
    val publicOut  = new ByteArrayOutputStream()
    val privateOut = new ByteArrayOutputStream()
    keyPair.writePrivateKey(privateOut)
    keyPair.writePublicKey(publicOut, comment)
    publicOut.close()
    privateOut.close()
    SshKey(publicOut.toString(CP), privateOut.toString(CP))
  }
}

case class SshKey(publicKey: String, privateKey: String)
