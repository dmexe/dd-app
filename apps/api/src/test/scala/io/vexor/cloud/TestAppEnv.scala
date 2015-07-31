package io.vexor.cloud

trait TestAppEnv extends AppEnv {
  override def appEnv = "test"
  val dbUrl = appConfig.getString("db.url")
}
