package scalapb.zio_grpc.client

import zio.test._

object ZClientCallSpec extends ZIOSpecDefault {
  def spec = test("existing Scala.js call implementations inherit the readiness default") {
    val call = new ZClientCall[Unit, Unit] {}
    call.isReady.map(ready => assertTrue(ready))
  }
}
