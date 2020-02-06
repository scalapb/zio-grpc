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
import zio.stream.ZStream
import zio.URIO
import zio.stream.ZSink
import zio.test.TestAspect._

object TestServiceSpec extends DefaultRunnableSpec {
  def statusCode(a: Assertion[Code]) =
    hasField[Status, Code]("code", _.getCode, a)

  val testServiceLayer: ZLayer[Clock with Console, Nothing, TestServiceImpl] =
    ZLayer.fromServiceM(TestServiceImpl.make)
    ZLayer.fromEffect(TestServiceImpl.make.map(Has(_)))

  val serverLayer: ZLayer[TestServiceImpl, Nothing, Server] =
    ZLayer.fromServiceManaged { service: TestService.Service =>
      (for {
        rts <- ZManaged.fromEffect(ZIO.runtime[R])
        mgd <- Server.managed(
          ServerBuilder
            .forPort(0)
            .addService(TestService.bindService(rts, service))
        )
      } yield Has(mgd)).orDie
    }

  val clientLayer: ZLayer[Server, Throwable, TestService] =
    ZLayer.fromServiceManaged { ss: Server.Service =>
      ZManaged.fromEffect(ss.port) >>= { port: Int =>
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
          fiber <- TestService.unary(Request(Request.Scenario.DELAY, in = 12)).fork
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
          TestService.serverStreaming(
            Request(Request.Scenario.DELAY, in = 12)
          ).peel(ZSink.collectAllN[Response](2)).use {
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

  def spec =
    suite("AllSpecs")(unarySuite, serverStreamingSuite)
      .provideLayer(testServiceLayer >>> serverLayer)
}
