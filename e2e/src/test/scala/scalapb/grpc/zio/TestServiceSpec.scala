package scalapb.grpc.zio

import zio.test._
import zio.test.Assertion._
import zio.ZIO
import io.grpc.ServerBuilder
import io.grpc.ManagedChannelBuilder
import zio.ZManaged
import scalapb.grpc.zio.testservice._
import io.grpc.Status
import io.grpc.Status.Code
import scalapb.grpc.zio.testservice.testService.TestService
import scalapb.grpc.zio.server.TestServiceImpl
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
        mgd <- scalapb.grpc.zio.Server.managed(
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

  def spec =
    suite("TestServiceSpec")(
      testM("unary request returns successful response") {
        for {
          resp <- TestService.>.unary(Request(Request.Scenario.OK, in = 12))
        } yield assert(resp)(equalTo(Response("Res12")))
      },
      testM("unary request returns correct error response") {
        for {
          resp <- TestService.>.unary(Request(Request.Scenario.ERROR, in = 12)).run
          x <- zio.test.environment.TestConsole.output
        } yield assert(resp)(
          fails(statusCode(equalTo((Status.INTERNAL.getCode)))))
      },
      testM("client interrupts are caught by server") {
        for {
          fiber <- TestService.>.unary(Request(Request.Scenario.DELAY, in = 12)).fork
          _ <- ZIO.accessM[TestServiceImpl](_.get.awaitReceived)
          _ <- fiber.interrupt
          exit <- ZIO.accessM[TestServiceImpl](_.get.awaitExit)
        } yield assert(exit.interrupted)(isTrue)
      },
      testM("server crashes are reported to client") {
        for {
          resp <- TestService.>.unary(Request(Request.Scenario.DIE, in = 12)).run
        } yield assert(resp)(fails(statusCode(equalTo(Status.INTERNAL.getCode))))
      }
    ).provideLayer(layer ++ TestConsole.any)
}
