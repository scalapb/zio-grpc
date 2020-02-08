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
import zio.Has
import zio.stream.{ZStream, ZSink, Stream}
import zio.URIO
import zio.test.TestAspect._
import scalapb.zio_grpc.testservice.Request.Scenario
import zio.ZQueue

object TestServiceSpec extends DefaultRunnableSpec {
  def statusCode(a: Assertion[Code]) =
    hasField[Status, Code]("code", _.getCode, a)

  val serverLayer: ZLayer[TestServiceImpl, Nothing, Server] =
    ZLayer.fromServiceManaged { service: TestServiceImpl.Service =>
      (for {
        rts <- ZManaged.fromEffect(ZIO.runtime[Any])
        mgd <- Server.managed(
          ServerBuilder
            .forPort(0)
            .addService(TestService.bindService(rts, service))
        )
      } yield Has(mgd)).orDie
    }

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
          fails(statusCode(equalTo((Status.INTERNAL.getCode))))
        )
      },
      testM("catches client interrupts") {
        for {
          fiber <- TestService
            .unary(Request(Request.Scenario.DELAY, in = 12))
            .fork
          _ <- ZIO.accessM[TestServiceImpl](_.get.awaitReceived)
          _ <- fiber.interrupt
          exit <- ZIO.accessM[TestServiceImpl](_.get.awaitExit)
        } yield assert(exit.interrupted)(isTrue)
      },
      testM("returns response on failures") {
        assertM(
          TestService.unary(Request(Request.Scenario.DIE, in = 12)).run
        )(
          fails(statusCode(equalTo(Status.INTERNAL.getCode)))
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
          tuple(isEmpty, isSome(statusCode(equalTo(Status.INTERNAL.getCode()))))
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
            isSome(statusCode(equalTo(Status.INTERNAL.getCode())))
          )
        )
      },
      testM("catches client cancellations") {
        assertM(
          TestService
            .serverStreaming(
              Request(Request.Scenario.DELAY, in = 12)
            )
            .peel(ZSink.collectAllN[Response](2))
            .use {
              case (b, rem) =>
                for {
                  _ <- ZIO.effect(println("FOO"))
                  reminderFiber <- rem.runCollect.fork
                  _ <- reminderFiber.interrupt
                  exit <- ZIO.accessM[TestServiceImpl](_.get.awaitExit)
                } yield exit.interrupted
            }
        )(isTrue)
      } @@ ignore,
      testM("returns failure when failure") {
        assertM(
          collectWithError(
            TestService.serverStreaming(
              Request(Request.Scenario.DIE, in = 12)
            )
          )
        )(
          tuple(isEmpty, isSome(statusCode(equalTo(Status.INTERNAL.getCode()))))
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
        )(fails(statusCode(equalTo((Status.INTERNAL.getCode)))))
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
          _ <- ZIO.accessM[TestServiceImpl](_.get.awaitReceived)
          _ <- fiber.interrupt
          exit <- ZIO.accessM[TestServiceImpl](_.get.awaitExit)
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
        )(fails(statusCode(equalTo((Status.INTERNAL.getCode)))))
      }
    )

  def bidiStreamingSuite =
    suite("bidi streaming request")(
      testM("returns successful response") {
        assertM((for {
          q <- ZQueue.unbounded[Request].toManaged_
          str = TestService.bidiStreaming(
            Stream(Request(Scenario.OK, in = 1)) ++ Stream.fromQueue(q)
          )
          p1 <- str.peel(ZSink.collectAllN[Response](1))
          (r1, rest1) = p1
          _ <- q.offer(Request(Scenario.OK, in = 3)).toManaged_
          p2 <- rest1.peel(ZSink.collectAllN[Response](3))
          (r2, rest2) = p2
          _ <- q.offer(Request(Scenario.OK, in = 5)).toManaged_
          p3 <- rest2.peel(ZSink.collectAllN[Response](5))
          (r3, rest3) = p3
          _ <- q.shutdown.toManaged_
          r4 <- rest3.runCollect.toManaged_
        } yield (r1, r2, r3, r4)).use(ZIO.succeed(_)))(
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
      testM("returns successful response") {}
    )

  val f = TestServiceImpl.live >>> (TestServiceImpl.any ++ serverLayer) >>> (TestServiceImpl.any ++ clientLayer ++ Annotations.live)
  def spec =
    suite("AllSpecs")(
      unarySuite,
      serverStreamingSuite,
      clientStreamingSuite,
      bidiStreamingSuite
    ).provideLayer(f)
}
