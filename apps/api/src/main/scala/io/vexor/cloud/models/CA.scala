package io.vexor.cloud.models

import io.vexor.cloud.KeyGen

class CA(id: String, subject: String, re: KeyGen.Result) {
  val cert       = re.cert
  val privateKey = re.privateKey
  val publicKey  = re.publicKey
}

object CA {
  val issuer = "cloud.vexor.io"

  def apply(id: String, subject: String, prop: PropertiesTable): CA = {
    val certName = s"$id-cert.pem"
    val keyName  = s"$id-key.pem"

    val re = loadFromDb(prop, certName, keyName) getOrElse genAndSaveToDb(prop, subject, certName, keyName)
    new CA(id, subject, re)
  }

  private def loadFromDb(prop: PropertiesTable, certName: String, keyName: String) : Option[KeyGen.Result] = {
    val pem =
      for {
        cert <- prop.one(certName)
        key  <- prop.one(keyName)
      } yield KeyGen.Pem(cert.value, key.value)

    pem map KeyGen.fromPEM
  }

  private def genAndSaveToDb(prop: PropertiesTable, subject: String, certName: String, keyName: String): KeyGen.Result = {
    val ca  = KeyGen.genCa(issuer, subject)
    val pem = KeyGen.toPEM(ca)

    prop.save(PropertiesTable.Record(certName, pem.cert))
    prop.save(PropertiesTable.Record(keyName,  pem.privateKey))

    ca
  }
}
