package scalapb.zio_grpc

import zio.test._
import zio.test.Assertion._
import zio.ZIO
import io.grpc.ServerBuilder
import io.grpc.ManagedChannelBuilder
import zio.ZManaged
import scalapb.zio_grpc.testservice._
import io.grpc.Status
import io.grpc.Status.Code
import scalapb.zio_grpc.testservice.testService.TestService
import scalapb.zio_grpc.server.TestServiceImpl
import zio.ZLayer
import zio.test.environment._
import zio.Has
import zio.Managed

object TestServiceSpec extends DefaultRunnableSpec {
  def statusCode(a: Assertion[Code]) =
    hasField[Status, Code]("code", _.getCode, a)

  def testServiceManaged: Managed[Nothing, TestServiceImpl] = ZManaged.fromEffect(TestServiceImpl.make.map(Has(_)))

  def serverManaged[R](service: TestService.Service[R]): ZManaged[R, Nothing, Server] = {
    (for {
        rts <- ZManaged.fromEffect(ZIO.runtime[R])
        mgd <- scalapb.zio_grpc.Server.managed(
          ServerBuilder
            .forPort(0)
            .addService(TestService.bindService(rts, service))
        )
      } yield mgd.asEnv).orDie
    }

    def clientManaged(port: Int): Managed[Nothing, TestService] = {
        ZManagedChannel.make(
          ManagedChannelBuilder.forAddress("localhost", port).usePlaintext()
        ).map(TestService.clientService(_)).orDie
    }

  def layer = ZLayer(for {
    service <- testServiceManaged
    server <- serverManaged(service.get)
    port <- ZManaged.fromEffect(server.get.port).orDie
    client <- clientManaged(port)
  } yield (client ++ service))

  def unarySuite =
    suite("unary request")(
      testM("returns successful response") {
        for {
          resp <- TestService.>.unary(Request(Request.Scenario.OK, in = 12))
        } yield assert(resp)(equalTo(Response("Res12")))
      },
      testM("returns correct error response") {
        for {
          resp <- TestService.>.unary(Request(Request.Scenario.ERROR, in = 12)).run
        } yield assert(resp)(
          fails(statusCode(equalTo((Status.INTERNAL.getCode)))))
      },
      testM("catches client interrupts") {
        for {
          fiber <- TestService.>.unary(Request(Request.Scenario.DELAY, in = 12)).fork
          _ <- ZIO.accessM[TestServiceImpl](_.get.awaitReceived)
          _ <- fiber.interrupt
          exit <- ZIO.accessM[TestServiceImpl](_.get.awaitExit)
        } yield assert(exit.interrupted)(isTrue)
      },
      testM("returns response on failures") {
        for {
          resp <- TestService.>.unary(Request(Request.Scenario.DIE, in = 12)).run
        } yield assert(resp)(fails(statusCode(equalTo(Status.INTERNAL.getCode))))
      }
    ).provideLayer(layer ++ TestConsole.any)

  def spec = suite("AllSpecs")(unarySuite)
}
