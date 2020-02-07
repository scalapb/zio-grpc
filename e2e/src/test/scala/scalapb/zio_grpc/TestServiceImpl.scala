package scalapb.zio_grpc

import scalapb.zio_grpc.testservice.Request
import zio.ZIO
import scalapb.zio_grpc.testservice.Response
import io.grpc.Status
import scalapb.zio_grpc.testservice.Request.Scenario
import zio.clock.Clock
import zio.console.Console
import zio.Has
import zio.Promise
import zio.Exit
import zio.ZLayer
import zio.stream.ZStream

package object server {

  type TestServiceImpl = Has[TestServiceImpl.Service]

  object TestServiceImpl {

    class Service(
        requestReceived: zio.Promise[Nothing, Unit],
        exit: zio.Promise[Nothing, Exit[Status, Response]])(clock: Clock.Service, console: Console.Service) extends testservice.testService.TestService.Service[Any] {
      def unary(request: Request): ZIO[Any, Status, Response] =
        (requestReceived.succeed(()) *> (request.scenario match {
          case Scenario.OK =>
            console.putStrLn("foo") *> ZIO.succeed(Response(out = "Res" + request.in.toString))
          case Scenario.ERROR_NOW =>
            ZIO.fail(Status.INTERNAL.withDescription("FOO!"))
          case Scenario.DELAY => ZIO.never
          case Scenario.DIE   => ZIO.die(new RuntimeException("FOO"))
          case _              => ZIO.fail(Status.UNKNOWN)
        })).onExit(exit.succeed(_))

      def serverStreaming(
          request: Request
      ): ZStream[Any, Status, Response] = {
        ZStream
          .fromEffect(requestReceived.succeed(()))
          .drain ++ (request.scenario match {
          case Scenario.OK =>
            ZStream(Response(out = "X1"), Response(out = "X2"))
          case Scenario.ERROR_NOW =>
            ZStream.fail(Status.INTERNAL.withDescription("FOO!"))
          case Scenario.ERROR_AFTER =>
            ZStream(Response(out = "X1"), Response(out = "X2")) ++ ZStream.fail(
              Status.INTERNAL.withDescription("FOO!")
            )
          case Scenario.DELAY =>
            ZStream(Response(out = "X1"), Response(out = "X2")) ++ ZStream.never
          case Scenario.DIE => ZStream.die(new RuntimeException("FOO"))
          case _            => ZStream.fail(Status.UNKNOWN)
        }).catchAllCause { c =>
          ZStream.fromEffect(exit.succeed(Exit.halt(c))).drain ++ ZStream.halt(
            c
          )
        }
      }

      def awaitReceived = requestReceived.await

      def awaitExit = exit.await
    }

    def make(clock: Clock.Service, console: Console.Service): zio.IO[Nothing, TestServiceImpl] = for {
      p1 <- Promise.make[Nothing, Unit]
      p2 <- Promise.make[Nothing, Exit[Status, Response]]
    } yield Has(new Service(p1, p2)(clock, console))

    val live: ZLayer[Clock with Console, Nothing, TestServiceImpl] =
     ZLayer.fromServicesM[Clock.Service, Console.Service, Any, Nothing, TestServiceImpl](make(_, _))

    val any: ZLayer[TestServiceImpl, Nothing, TestServiceImpl] = ZLayer.requires
  }
}
