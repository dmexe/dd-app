package io.vexor.cloud.models

import io.vexor.cloud.TestAppEnv
import org.scalatest.{BeforeAndAfterEach, BeforeAndAfterAll, Matchers, WordSpecLike}

class SshKeySpec extends WordSpecLike with Matchers with BeforeAndAfterAll with BeforeAndAfterEach with TestAppEnv {

  val reg = ModelRegistry(dbUrl, "SshKeySpec").get
  val db  = reg.properties

  override def beforeAll() : Unit = {
    db.down()
    db.up()
  }

  override def afterAll() : Unit = {
    db.down()
    reg.db.close()
  }

  override def afterEach() : Unit = {
    db.truncate()
  }

  "A SshKey" must {
    "successfully generate ssh key" in {
      val sshKey = SshKey(db, "test").get

      sshKey.publicKey should startWith("ssh-rsa")
      sshKey.publicKey should endWith("test\n")

      sshKey.privateKey should include("BEGIN RSA PRIVATE KEY")

      val sshKey2 = SshKey(db, "test").get
      sshKey2.privateKey should be(sshKey.privateKey)
      sshKey2.publicKey should be(sshKey.publicKey)
    }
  }
}