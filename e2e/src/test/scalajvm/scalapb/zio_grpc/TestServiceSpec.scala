package scalapb.zio_grpc

import io.grpc.{ManagedChannelBuilder, ServerBuilder, Status, StatusException}
import scalapb.zio_grpc.server.TestServiceImpl
import scalapb.zio_grpc.testservice.Request.Scenario
import scalapb.zio_grpc.testservice.ZioTestservice.TestServiceClient
import scalapb.zio_grpc.testservice._
import zio.{durationInt, Fiber, Queue, ZIO, ZLayer}
import zio.stream.{Stream, ZStream}
import zio.test.Assertion._
import zio.test.TestAspect.{flaky, timeout, withLiveClock}
import zio.test._
import TestUtils._

object TestServiceSpec extends ZIOSpecDefault with CommonTestServiceSpec {
  val serverLayer: ZLayer[TestServiceImpl, Throwable, Server] =
    ServerLayer.fromEnvironment[TestServiceImpl.Service](ServerBuilder.forPort(0))

  val clientLayer: ZLayer[Server, Nothing, TestServiceClient] =
    ZLayer.scoped[Server] {
      for {
        ss     <- ZIO.service[Server]
        port   <- ss.port.orDie
        ch      = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext()
        client <- TestServiceClient.scoped(ZManagedChannel(ch)).orDie
      } yield client
    }

  def unarySuiteJVM =
    suite("unary request")(
      test("catches client interrupts") {
        for {
          fiber <- TestServiceClient
                     .unary(Request(Request.Scenario.DELAY, in = 12))
                     .fork
          _     <- TestServiceImpl.awaitReceived
          _     <- fiber.interrupt
          exit  <- TestServiceImpl.awaitExit
        } yield assert(exit.isInterrupted)(isTrue)
      },
      test("setting deadline interrupts the servers") {
        // If the timeout is too short, gRPC may not make the call or send messages. While the
        // timeout below is set to 1s, it can still get missed on CI.
        // On the other hand, if we set it for too long it slows down the test. As a compromise,
        // we mark it a flaky.
        for {
          r    <- TestServiceClient.withTimeoutMillis(1000).unary(Request(Request.Scenario.DELAY, in = 12)).exit
          // The timeout below protects the test from getting hang if the call is discarded by grpc.
          exit <- TestServiceImpl.awaitExit.timeout(3.seconds)
        } yield assert(r)(fails(hasStatusCode(Status.DEADLINE_EXCEEDED))) && assert(exit.get.isInterrupted)(isTrue)
      } @@ flaky(100) @@ withLiveClock
    )

  def serverStreamingSuiteJVM =
    suite("server streaming request")(
      test("catches client cancellations") {
        assertZIO(for {
          fb   <- TestServiceClient
                    .serverStreaming(
                      Request(Request.Scenario.DELAY, in = 12)
                    )
                    .runCollect
                    .fork
          _    <- TestServiceImpl.awaitReceived
          _    <- fb.interrupt
          exit <- TestServiceImpl.awaitExit
        } yield exit)(fails(hasStatusCode(Status.CANCELLED)))
      }
    )

  def clientStreamingSuite =
    suite("client streaming request")(
      test("returns successful response") {
        assertZIO(
          TestServiceClient.clientStreaming(
            ZStream(
              Request(Scenario.OK, in = 17),
              Request(Scenario.OK, in = 12),
              Request(Scenario.OK, in = 33)
            )
          )
        )(equalTo(Response("62")))
      },
      test("returns successful response on empty stream") {
        assertZIO(
          TestServiceClient.clientStreaming(
            ZStream.empty
          )
        )(equalTo(Response("0")))
      },
      test("returns correct error response") {
        assertZIO(
          TestServiceClient
            .clientStreaming(
              ZStream(
                Request(Scenario.OK, in = 17),
                Request(Scenario.OK, in = 12),
                Request(Scenario.ERROR_NOW, in = 33)
              )
            )
            .exit
        )(fails(hasStatusCode(Status.INTERNAL)))
      },
      test("catches client cancellation") {
        assertZIO(for {
          fiber <- TestServiceClient
                     .clientStreaming(
                       ZStream(
                         Request(Scenario.OK, in = 17),
                         Request(Scenario.OK, in = 12),
                         Request(Scenario.DELAY, in = 33)
                       )
                     )
                     .fork
          _     <- TestServiceImpl.awaitDelayReceived
          _     <- fiber.interrupt
          exit  <- TestServiceImpl.awaitExit
        } yield exit)(isInterrupted)
      },
      test("returns response on failures") {
        assertZIO(
          TestServiceClient
            .clientStreaming(
              ZStream(
                Request(Scenario.OK, in = 17),
                Request(Scenario.OK, in = 12),
                Request(Scenario.DIE, in = 33)
              )
            )
            .exit
        )(fails(hasStatusCode(Status.INTERNAL)))
      },
      test("returns response on failures for infinite input") {
        assertZIO(
          TestServiceClient
            .clientStreaming(
              ZStream.repeat(Request(Scenario.DIE, in = 33))
            )
            .exit
        )(fails(hasStatusCode(Status.INTERNAL)))
      } @@ timeout(5.seconds)
    )

  case class BidiFixture[Req, Res](
      in: Queue[Res],
      out: Queue[Option[Req]],
      fiber: Fiber[StatusException, Unit]
  ) {
    def send(r: Req) = out.offer(Some(r))

    def receive(n: Int) = ZIO.collectAll(ZIO.replicate(n)(in.take))

    def halfClose = out.offer(None)
  }

  object BidiFixture {
    def apply[R, Req, Res](
        call: Stream[StatusException, Req] => ZStream[R, StatusException, Res]
    ): zio.URIO[R, BidiFixture[Req, Res]] =
      for {
        in    <- Queue.unbounded[Res]
        out   <- Queue.unbounded[Option[Req]]
        fiber <- call(ZStream.fromQueue(out).collectWhileSome).foreach(in.offer).fork
      } yield BidiFixture(in, out, fiber)
  }

  def bidiStreamingSuite =
    suite("bidi streaming request")(
      test("returns successful response") {
        assertZIO(for {
          bf   <- BidiFixture(TestServiceClient.bidiStreaming)
          _    <- bf.send(Request(Scenario.OK, in = 1))
          f1   <- bf.receive(1)
          _    <- bf.send(Request(Scenario.OK, in = 3))
          f3   <- bf.receive(3)
          _    <- bf.send(Request(Scenario.OK, in = 5))
          f5   <- bf.receive(5)
          _    <- bf.halfClose
          done <- bf.receive(1)
        } yield (f1, f3, f5, done))(
          equalTo(
            (
              List(Response("1")),
              List.fill(3)(Response("3")),
              List.fill(5)(Response("5")),
              List(Response("DONE"))
            )
          )
        )
      },
      test("returns correct error response") {
        assertZIO(for {
          bf <- BidiFixture(TestServiceClient.bidiStreaming)
          _  <- bf.send(Request(Scenario.OK, in = 1))
          f1 <- bf.receive(1)
          _  <- bf.send(Request(Scenario.ERROR_NOW, in = 3))
          _  <- bf.halfClose
          j  <- bf.fiber.join.exit
        } yield (f1, j))(
          tuple(
            equalTo(List(Response("1"))),
            fails(hasDescription("Intentional error") && hasStatusCode(Status.INTERNAL))
          )
        )
      },
      test("catches client interrupts") {
        assertZIO(
          for {
            testServiceImpl <- ZIO.environment[TestServiceImpl]
            collectFiber    <- collectWithError(
                                 TestServiceClient.bidiStreaming(
                                   ZStream(
                                     Request(Scenario.OK, in = 17)
                                   ) ++ ZStream.fromZIO(testServiceImpl.get.awaitReceived).drain
                                     ++ ZStream.fail(Status.CANCELLED.asException())
                                 )
                               ).fork
            _               <- testServiceImpl.get.awaitExit
            result          <- collectFiber.join
          } yield result
        )(
          tuple(anything, isSome(hasStatusCode(Status.CANCELLED)))
        )
      },
      test("returns response on failures") {
        assertZIO(
          TestServiceClient
            .bidiStreaming(
              ZStream(
                Request(Scenario.OK, in = 17),
                Request(Scenario.OK, in = 12),
                Request(Scenario.DIE, in = 33)
              )
            )
            .runCollect
            .exit
        )(fails(hasStatusCode(Status.INTERNAL)))
      }
    )

  val layers = TestServiceImpl.live >>>
    (TestServiceImpl.any ++ serverLayer) >>>
    (TestServiceImpl.any ++ clientLayer ++ Annotations.live)

  def spec =
    suite("TestServiceSpec")(
      unarySuite,
      unarySuiteJVM,
      serverStreamingSuite,
      serverStreamingSuiteJVM,
      clientStreamingSuite,
      bidiStreamingSuite
    ).provideLayer(layers.orDie)
}
