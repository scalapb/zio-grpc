package scalapb.zio_grpc

import scalapb.zio_grpc.testservice.Request
import zio.ZIO
import scalapb.zio_grpc.testservice.Response
import io.grpc.Status
import scalapb.zio_grpc.testservice.Request.Scenario
import zio.Clock
import zio.Console
import zio.Has
import zio.Promise
import zio.Exit
import zio.ZLayer
import zio.stream.{Stream, ZStream}

package object server {

  import zio.Schedule

  type TestServiceImpl = Has[TestServiceImpl.Service]

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
          .bracketExit(requestReceived.succeed(())) { (_, ex) =>
            ex.foldM(
              failed =>
                if (failed.interrupted)
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
            .foldM(0)((state, req) =>
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
        ((ZStream.fromEffect(requestReceived.succeed(())).drain ++
          (request.flatMap { r =>
            r.scenario match {
              case Scenario.OK        =>
                Stream(Response(r.in.toString))
                  .repeat(Schedule.recurs(r.in - 1))
              case Scenario.DELAY     => Stream.never
              case Scenario.DIE       => Stream.die(new RuntimeException("FOO"))
              case Scenario.ERROR_NOW =>
                // Stream.fromEffect(zio.console.putStrLn("*** Got error now!")).drain ++
                Stream.fail(Status.INTERNAL.withDescription("Intentional error"))
              case _                  => Stream.fail(Status.INVALID_ARGUMENT.withDescription(s"Got request: ${r.toProtoString}"))
            }
          } ++ Stream(Response("DONE"))))
          .ensuring(exit.succeed(Exit.succeed(Response()))))
          .provide(Has(clock) ++ Has(console))

      def awaitReceived = requestReceived.await

      def awaitDelayReceived = requestReceived.await

      def awaitExit = exit.await
    }

    def make(
        clock: Clock,
        console: Console,
    ): zio.IO[Nothing, TestServiceImpl.Service] =
      for {
        p1 <- Promise.make[Nothing, Unit]
        p2 <- Promise.make[Nothing, Unit]
        p3 <- Promise.make[Nothing, Exit[Status, Response]]
      } yield new Service(p1, p2, p3)(clock, console)

    def makeFromEnv: ZIO[Has[Clock] with Has[Console],Nothing,Service] =
       ZIO.accessZIO[Has[Clock] with Has[Console]](env => make(env.get[Clock], env.get[Console]))

    val live: ZLayer[Has[Clock] with Has[Console], Nothing, TestServiceImpl] =
      makeFromEnv.toLayer

    val any: ZLayer[TestServiceImpl, Nothing, TestServiceImpl] = ZLayer.requires

    def awaitReceived: ZIO[TestServiceImpl, Nothing, Unit] =
      ZIO.accessM(_.get.awaitReceived)

    def awaitDelayReceived: ZIO[TestServiceImpl, Nothing, Unit] =
      ZIO.accessM(_.get.awaitDelayReceived)

    def awaitExit: ZIO[TestServiceImpl, Nothing, Exit[Status, Response]] =
      ZIO.accessM(_.get.awaitExit)
  }
}
