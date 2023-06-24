package scalapb.zio_grpc

import scalapb.zio_grpc.testservice.ZioTestservice.TestServiceClient
import scalapb.zio_grpc.ZManagedChannel
import scalapb.grpc.Channels
import zio.test._

object TestSuiteApp extends ZIOSpecDefault with CommonTestServiceSpec {
  def clientLayer =
    TestServiceClient.live(ZManagedChannel(Channels.grpcwebChannel("http://localhost:8080")))

  def spec =
    suite("TestServiceSpec")(
      unarySuite,
      serverStreamingSuite
    ).provideLayer(clientLayer.orDie)
}
