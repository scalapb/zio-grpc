package scalapb.zio_grpc

import scalapb.zio_grpc.testservice.ZioTestservice.TestServiceClient
import scalapb.zio_grpc.ZManagedChannel
import scalapb.grpc.Channels
import zio.test._
import org.scalajs.dom

object TestSuiteApp extends ZIOSpecDefault with CommonTestServiceSpec {
  val port = new dom.URL(dom.window.location.href).searchParams.get("port").toInt

  dom.console.log(s"Port is $port")

  def clientLayer =
    TestServiceClient.live(ZManagedChannel(Channels.grpcwebChannel(s"http://localhost:$port")))

  def spec =
    suite("TestServiceSpec")(
      unarySuite,
      serverStreamingSuite
    ).provideLayer(clientLayer.orDie)
}
