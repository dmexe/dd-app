package io.vexor.docker.api

trait TestAppEnv extends AppEnv {
  override def appEnv = "test"
  val dbUrl = appConfig.getString("db.url")
}
