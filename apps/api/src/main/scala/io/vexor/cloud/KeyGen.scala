package io.vexor.cloud

import java.io.{StringReader, StringWriter}
import java.math.BigInteger
import java.security._
import java.security.cert.X509Certificate
import java.util.Date
import javax.security.auth.x500.X500Principal

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509._
import org.bouncycastle.cert.jcajce.{JcaX509CertificateConverter, JcaX509v3CertificateBuilder}
import org.bouncycastle.cert.{X509CertificateHolder, X509v3CertificateBuilder}
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.openssl.{PEMKeyPair, PEMParser, PEMWriter}
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.pkcs.jcajce.{JcaPKCS10CertificationRequest, JcaPKCS10CertificationRequestBuilder}


object KeyGen {

  Security.addProvider(new BouncyCastleProvider())

  def genCa(issuerCN: String, subjectCN: String): Result = {
    val keyPair = genKeyPair()
    val keyInfo = SubjectPublicKeyInfo.getInstance(keyPair.getPublic.getEncoded)

    val builder = new X509v3CertificateBuilder(
      new X500Name(s"CN=$issuerCN"),
      serial(),
      issuedDate(),
      expiryDate(),
      new X500Name(s"CN=$subjectCN"),
      keyInfo
    )

    builder
      .addExtension(
        Extension.basicConstraints,
        false,
        new BasicConstraints(true)
      )

    val sigGen = new JcaContentSignerBuilder("SHA1WithRSAEncryption").setProvider("BC").build(keyPair.getPrivate)
    val cert   = new JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(sigGen))

    Result(cert, keyPair.getPrivate, keyPair.getPublic)
  }

  def genCert(ca: Result, cnName: String): Result = {

    val (csr, keyPair) = getClientCsr(cnName)

    val jcaRequest = new JcaPKCS10CertificationRequest(csr)
    val certificateBuilder = new JcaX509v3CertificateBuilder(
      ca.cert,
      serial(),
      issuedDate(),
      expiryDate(),
      jcaRequest.getSubject,
      jcaRequest.getPublicKey
    )

    certificateBuilder
      .addExtension(
          Extension.extendedKeyUsage,
          false,
          new ExtendedKeyUsage(KeyPurposeId.id_kp_clientAuth)
        )

    val signer = new JcaContentSignerBuilder("SHA1WithRSAEncryption").setProvider("BC").build(ca.privateKey)
    val cert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(certificateBuilder.build(signer))

    Result(cert, keyPair.getPrivate, keyPair.getPublic)
  }

  def toPEM(res: Result): Pem = {
    val cert       = toPemString(res.cert)
    val privateKey = toPemString(res.privateKey)
    Pem(cert, privateKey)
  }

  def fromPEM(pem: Pem): Result = {
    val keyPair = readPemKeyPair(pem.privateKey)
    val cert    = readPemCert(pem.cert)

    Result(cert, keyPair.getPrivate, keyPair.getPublic)
  }

  private def toPemString(obj: AnyRef): String = {
    val str    = new StringWriter()
    val writer = new PEMWriter(str)
    writer.writeObject(obj)
    writer.close()
    str.close()
    str.toString
  }

  private def readPemCert(pem: String): X509Certificate = {
    val str = new StringReader(pem)
    val par = new PEMParser(str)
    val obj = par.readObject().asInstanceOf[X509CertificateHolder]
    new JcaX509CertificateConverter().setProvider("BC").getCertificate(obj)
  }

  private def readPemKeyPair(pem: String): KeyPair = {
    val str = new StringReader(pem)
    val par = new PEMParser(str)
    val obj = par.readObject().asInstanceOf[PEMKeyPair]
    val con = new JcaPEMKeyConverter()
    con.getKeyPair(obj)
  }

  private def serial(): BigInteger = {
    BigInteger.valueOf(System.currentTimeMillis())
  }

  private def issuedDate() : Date = {
    new Date(System.currentTimeMillis() - (24L * 60 * 60 * 1000))
  }

  private def expiryDate() : Date = {
    new Date(System.currentTimeMillis() + (3L * 365 * 24 * 60 * 60 * 1000))
  }

  private def genKeyPair(): KeyPair = {
    val keyGen = KeyPairGenerator.getInstance("RSA", "BC")
    keyGen.initialize(2048)
    val keyPair = keyGen.generateKeyPair()
    keyPair
  }

  private def getClientCsr(cnName: String) = {
    val keyPair = genKeyPair()

    val p10Builder = new JcaPKCS10CertificationRequestBuilder(
      new X500Principal(s"CN=$cnName"), keyPair.getPublic
    )

    val csBuilder = new JcaContentSignerBuilder("SHA256withRSA")
    val signer    = csBuilder.build(keyPair.getPrivate)
    val csr       = p10Builder.build(signer)
    (csr, keyPair)
  }

  case class Result(cert: X509Certificate, privateKey: PrivateKey, publicKey: PublicKey)
  case class Pem(cert: String, privateKey: String)
}
