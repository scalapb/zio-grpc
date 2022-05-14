package scalapb.zio_grpc

import scalapb.zio_grpc.testservice.Request
import zio.{Clock, Console, Exit, Promise, ZIO, ZLayer}
import scalapb.zio_grpc.testservice.Response
import io.grpc.Status
import scalapb.zio_grpc.testservice.Request.Scenario
import zio.stream.{Stream, ZStream}
import zio.ZEnvironment

package object server {

  import zio.Schedule

  type TestServiceImpl = TestServiceImpl.Service

  object TestServiceImpl {

    class Service(
        requestReceived: zio.Promise[Nothing, Unit],
        delayReceived: zio.Promise[Nothing, Unit],
        exit: zio.Promise[Nothing, Exit[Status, Response]]
    )(clock: Clock, console: Console)
        extends testservice.ZioTestservice.TestService {
      def unary(request: Request): ZIO[Any, Status, Response] =
        (requestReceived.succeed(()) *> (request.scenario match {
          case Scenario.OK        =>
            ZIO.succeed(
              Response(out = "Res" + request.in.toString)
            )
          case Scenario.ERROR_NOW =>
            ZIO.fail(Status.INTERNAL.withDescription("FOO!"))
          case Scenario.DELAY     => ZIO.never
          case Scenario.DIE       => ZIO.die(new RuntimeException("FOO"))
          case _                  => ZIO.fail(Status.UNKNOWN)
        })).onExit(exit.succeed(_))

      def serverStreaming(
          request: Request
      ): ZStream[Any, Status, Response] =
        ZStream
          .acquireReleaseExitWith(requestReceived.succeed(())) { (_, ex) =>
            ex.foldZIO(
              failed =>
                if (failed.isInterrupted || failed.isInterruptedOnly)
                  exit.succeed(Exit.fail(Status.CANCELLED))
                else exit.succeed(Exit.fail(Status.UNKNOWN)),
              _ => exit.succeed(Exit.succeed(Response()))
            )
          }
          .flatMap { _ =>
            request.scenario match {
              case Scenario.OK          =>
                ZStream(Response(out = "X1"), Response(out = "X2"))
              case Scenario.ERROR_NOW   =>
                ZStream.fail(Status.INTERNAL.withDescription("FOO!"))
              case Scenario.ERROR_AFTER =>
                ZStream(Response(out = "X1"), Response(out = "X2")) ++ ZStream
                  .fail(
                    Status.INTERNAL.withDescription("FOO!")
                  )
              case Scenario.DELAY       =>
                ZStream(
                  Response(out = "X1"),
                  Response(out = "X2")
                ) ++ ZStream.never
              case Scenario.DIE         => ZStream.die(new RuntimeException("FOO"))
              case _                    => ZStream.fail(Status.UNKNOWN)
            }
          }

      def clientStreaming(
          request: Stream[Status, Request]
      ): ZIO[Any, Status, Response] =
        requestReceived.succeed(()) *>
          request
            .runFoldZIO(0)((state, req) =>
              req.scenario match {
                case Scenario.OK        => ZIO.succeed(state + req.in)
                case Scenario.DELAY     => delayReceived.succeed(()) *> ZIO.never
                case Scenario.DIE       => ZIO.die(new RuntimeException("foo"))
                case Scenario.ERROR_NOW =>
                  ZIO.fail((Status.INTERNAL.withDescription("InternalError")))
                case _: Scenario        => ZIO.fail(Status.UNKNOWN)
              }
            )
            .map(r => Response(r.toString))
            .onExit(exit.succeed(_))

      def bidiStreaming(
          request: Stream[Status, Request]
      ): Stream[Status, Response] =
        (ZStream.fromZIO(requestReceived.succeed(())).drain ++
          (request.flatMap { r =>
            r.scenario match {
              case Scenario.OK        =>
                ZStream(Response(r.in.toString))
                  .repeat(Schedule.recurs(r.in - 1))
              case Scenario.DELAY     => ZStream.never
              case Scenario.DIE       => ZStream.die(new RuntimeException("FOO"))
              case Scenario.ERROR_NOW =>
                ZStream.fail(Status.INTERNAL.withDescription("Intentional error"))
              case _                  => ZStream.fail(Status.INVALID_ARGUMENT.withDescription(s"Got request: ${r.toProtoString}"))
            }
          } ++ ZStream(Response("DONE")))
            .ensuring(exit.succeed(Exit.succeed(Response()))))
          .provideEnvironment(ZEnvironment(clock, console))

      def awaitReceived = requestReceived.await

      def awaitDelayReceived = requestReceived.await

      def awaitExit = exit.await
    }

    def make(
        clock: Clock,
        console: Console
    ): zio.IO[Nothing, TestServiceImpl.Service] =
      for {
        p1 <- Promise.make[Nothing, Unit]
        p2 <- Promise.make[Nothing, Unit]
        p3 <- Promise.make[Nothing, Exit[Status, Response]]
      } yield new Service(p1, p2, p3)(clock, console)

    def makeFromEnv: ZIO[Any, Nothing, Service] =
      for {
        clock   <- ZIO.clock
        console <- ZIO.console
        service <- make(clock, console)
      } yield service

    val live: ZLayer[Any, Nothing, TestServiceImpl] =
      ZLayer(makeFromEnv)

    val any: ZLayer[TestServiceImpl, Nothing, TestServiceImpl] = ZLayer.environment

    def awaitReceived: ZIO[TestServiceImpl, Nothing, Unit] =
      ZIO.environmentWithZIO(_.get.awaitReceived)

    def awaitDelayReceived: ZIO[TestServiceImpl, Nothing, Unit] =
      ZIO.environmentWithZIO(_.get.awaitDelayReceived)

    def awaitExit: ZIO[TestServiceImpl, Nothing, Exit[Status, Response]] =
      ZIO.environmentWithZIO(_.get.awaitExit)
  }
}
