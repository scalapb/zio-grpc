package scalapb.zio_grpc

import zio.test._
import zio.test.Assertion._
import zio.Fiber
import zio.ZIO
import zio.Queue
import io.grpc.ServerBuilder
import io.grpc.ManagedChannelBuilder
import zio.ZManaged
import scalapb.zio_grpc.testservice._
import io.grpc.Status
import io.grpc.Status.Code
import scalapb.zio_grpc.testservice.testService.TestService
import scalapb.zio_grpc.server.TestServiceImpl
import zio.ZLayer
import zio.stream.{ZStream, Stream}
import zio.URIO
import scalapb.zio_grpc.testservice.Request.Scenario
import zio.ZQueue

object TestServiceSpec extends DefaultRunnableSpec {
  def hasStatusCode(c: Status) =
    hasField[Status, Code]("code", _.getCode, equalTo(c.getCode))

  val serverLayer: ZLayer[TestServiceImpl, Nothing, Server] =
    Server.live[TestServiceImpl.Service](ServerBuilder.forPort(0))

  val clientLayer: ZLayer[Server, Nothing, TestService] =
    ZLayer.fromServiceManaged { ss: Server.Service =>
      ZManaged.fromEffect(ss.port).orDie >>= { port: Int =>
        ZManagedChannel
          .make(
            ManagedChannelBuilder.forAddress("localhost", port).usePlaintext()
          )
          .map(TestService.clientService(_))
          .orDie
      }
    }

  def unarySuite =
    suite("unary request")(
      testM("returns successful response") {
        assertM(TestService.unary(Request(Request.Scenario.OK, in = 12)))(
          equalTo(Response("Res12"))
        )
      },
      testM("returns correct error response") {
        assertM(
          TestService.unary(Request(Request.Scenario.ERROR_NOW, in = 12)).run
        )(
          fails(hasStatusCode(Status.INTERNAL))
        )
      },
      testM("catches client interrupts") {
        for {
          fiber <- TestService
            .unary(Request(Request.Scenario.DELAY, in = 12))
            .fork
          _ <- TestServiceImpl.awaitReceived
          _ <- fiber.interrupt
          exit <- TestServiceImpl.awaitExit
        } yield assert(exit.interrupted)(isTrue)
      },
      testM("returns response on failures") {
        assertM(
          TestService.unary(Request(Request.Scenario.DIE, in = 12)).run
        )(
          fails(hasStatusCode(Status.INTERNAL))
        )
      }
    )

  def collectWithError[R, E, A](
      zs: ZStream[R, E, A]
  ): URIO[R, (List[A], Option[E])] =
    zs.either
      .fold[Either[E, A], (List[A], Option[E])]((Nil, None)) {
        case ((l, _), Left(e))  => (l, Some(e))
        case ((l, e), Right(a)) => (a :: l, e)
      }
      .map { case (la, oe) => (la.reverse, oe) }

  def tuple[A, B](
      assertionA: Assertion[A],
      assertionB: Assertion[B]
  ): Assertion[(A, B)] =
    Assertion.assertionDirect("tuple")(
      Assertion.Render.param(assertionA),
      Assertion.Render.param(assertionB)
    ) { run =>
      assertionA.run(run._1) && assertionB.run(run._2)
    }

  def serverStreamingSuite =
    suite("server streaming request")(
      testM("returns successful response") {
        assertM(
          collectWithError(
            TestService.serverStreaming(Request(Request.Scenario.OK, in = 12))
          )
        )(equalTo((List(Response("X1"), Response("X2")), None)))
      },
      testM("returns correct error response") {
        assertM(
          collectWithError(
            TestService.serverStreaming(
              Request(Request.Scenario.ERROR_NOW, in = 12)
            )
          )
        )(
          tuple(isEmpty, isSome(hasStatusCode(Status.INTERNAL)))
        )
      },
      testM("returns correct error after two response") {
        assertM(
          collectWithError(
            TestService.serverStreaming(
              Request(Request.Scenario.ERROR_AFTER, in = 12)
            )
          )
        )(
          tuple(
            hasSize(equalTo(2)),
            isSome(hasStatusCode(Status.INTERNAL))
          )
        )
      },
      testM("catches client cancellations") {
        assertM(for {
          fb <- TestService
            .serverStreaming(
              Request(Request.Scenario.DELAY, in = 12)
            )
            .runCollect
            .fork
          _ <- TestServiceImpl.awaitReceived
          _ <- fb.interrupt
          exit <- TestServiceImpl.awaitExit
        } yield exit)(fails(hasStatusCode(Status.CANCELLED)))
      },
      testM("returns failure when failure") {
        assertM(
          collectWithError(
            TestService.serverStreaming(
              Request(Request.Scenario.DIE, in = 12)
            )
          )
        )(
          tuple(isEmpty, isSome(hasStatusCode(Status.INTERNAL)))
        )
      }
    )

  def clientStreamingSuite =
    suite("client streaming request")(
      testM("returns successful response") {
        assertM(
          TestService.clientStreaming(
            Stream(
              Request(Scenario.OK, in = 17),
              Request(Scenario.OK, in = 12),
              Request(Scenario.OK, in = 33)
            )
          )
        )(equalTo(Response("62")))
      },
      testM("returns successful response on empty stream") {
        assertM(
          TestService.clientStreaming(
            Stream.empty
          )
        )(equalTo(Response("0")))
      },
      testM("returns correct error response") {
        assertM(
          TestService
            .clientStreaming(
              Stream(
                Request(Scenario.OK, in = 17),
                Request(Scenario.OK, in = 12),
                Request(Scenario.ERROR_NOW, in = 33)
              )
            )
            .run
        )(fails(hasStatusCode(Status.INTERNAL)))
      },
      testM("catches client cancellation") {
        assertM(for {
          fiber <- TestService
            .clientStreaming(
              Stream(
                Request(Scenario.OK, in = 17),
                Request(Scenario.OK, in = 12),
                Request(Scenario.DELAY, in = 33)
              )
            )
            .fork
          _ <- TestServiceImpl.awaitReceived
          _ <- fiber.interrupt
          exit <- TestServiceImpl.awaitExit
        } yield exit.interrupted)(isTrue)
      },
      testM("returns response on failures") {
        assertM(
          TestService
            .clientStreaming(
              Stream(
                Request(Scenario.OK, in = 17),
                Request(Scenario.OK, in = 12),
                Request(Scenario.DIE, in = 33)
              )
            )
            .run
        )(fails(hasStatusCode(Status.INTERNAL)))
      }
    )

  case class BidiFixture[Req, Res](
      in: Queue[Res],
      out: Queue[Req],
      fiber: Fiber[Status, Unit]
  ) {
    def send(r: Req) = out.offer(r)

    def receive(n: Int) = ZIO.collectAll(ZIO.replicate(n)(in.take))

    def halfClose = out.shutdown
  }

  object BidiFixture {
    def apply[R, Req, Res](
        call: Stream[Status, Req] => ZStream[R, Status, Res]
    ): zio.URIO[R, BidiFixture[Req, Res]] =
      for {
        in <- ZQueue.unbounded[Res]
        out <- ZQueue.unbounded[Req]
        fiber <- call(Stream.fromQueue(out)).foreach(in.offer).fork
      } yield BidiFixture(in, out, fiber)
  }

  def bidiStreamingSuite =
    suite("bidi streaming request")(
      testM("returns successful response") {
        assertM(for {
          bf <- BidiFixture(TestService.bidiStreaming)
          _ <- bf.send(Request(Scenario.OK, in = 1))
          f1 <- bf.receive(1)
          _ <- bf.send(Request(Scenario.OK, in = 3))
          f3 <- bf.receive(3)
          _ <- bf.send(Request(Scenario.OK, in = 5))
          f5 <- bf.receive(5)
          _ <- bf.halfClose
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
      testM("returns correct error response") {
        assertM(for {
          bf <- BidiFixture(TestService.bidiStreaming)
          _ <- bf.send(Request(Scenario.OK, in = 1))
          f1 <- bf.receive(1)
          _ <- bf.send(Request(Scenario.ERROR_NOW, in = 3))
          _ <- bf.halfClose
          j <- bf.fiber.join.run
        } yield (f1, j))(
          tuple(
            equalTo(List(Response("1"))),
            fails(hasStatusCode(Status.INTERNAL))
          )
        )
      },
      testM("catches client interrupts") {
        assertM(
          for {
            testServiceImpl <- ZIO.environment[TestServiceImpl]
            collectFiber <- collectWithError(
              TestService.bidiStreaming(
                Stream(
                  Request(Scenario.OK, in = 17)
                ) ++ Stream.fromEffect(testServiceImpl.get.awaitReceived).drain
                  ++ Stream.fail(Status.CANCELLED)
              )
            ).fork
            _ <- testServiceImpl.get.awaitExit
            result <- collectFiber.join
          } yield result
        )(
          tuple(anything, isSome(hasStatusCode(Status.CANCELLED)))
        )
      },
      testM("returns response on failures") {
        assertM(
          TestService
            .bidiStreaming(
              Stream(
                Request(Scenario.OK, in = 17),
                Request(Scenario.OK, in = 12),
                Request(Scenario.DIE, in = 33)
              )
            )
            .runCollect
            .run
        )(fails(hasStatusCode(Status.INTERNAL)))
      }
    )

  val layers = TestServiceImpl.live >>>
    (TestServiceImpl.any ++ serverLayer) >>>
    (clientLayer ++ TestServiceImpl.any ++ Annotations.live)

  def spec =
    suite("AllSpecs")(
      unarySuite,
      serverStreamingSuite,
      clientStreamingSuite,
      bidiStreamingSuite
    ).provideLayer(layers)
}
