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
import zio.UIO
import zio.Exit

package object server {

  type TestServiceImpl = Has[TestServiceImpl.Service]

  object TestServiceImpl {
    class Service(
        requestReceived: zio.Promise[Nothing, Unit],
        exit: zio.Promise[Nothing, Exit[Status, Response]]
    ) extends testservice.testService.TestService.Service[Clock with Console] {
      def unary(request: Request): ZIO[Clock with Console, Status, Response] =
        (requestReceived.succeed(()) *> (request.scenario match {
          case Scenario.OK =>
            ZIO.succeed(Response(out = "Res" + request.in.toString))
          case Scenario.ERROR =>
            ZIO.fail(Status.INTERNAL.withDescription("FOO!"))
          case Scenario.DELAY           => ZIO.never
          case Scenario.DIE             => ZIO.die(new RuntimeException("FOO"))
          case Scenario.Unrecognized(_) => ZIO.fail(Status.UNKNOWN)
        })).onExit(exit.succeed(_))

      def awaitReceived = requestReceived.await

      def awaitExit = exit.await
    }

    def make: UIO[Service] =
      ZIO.mapN(
        Promise.make[Nothing, Unit],
        Promise.make[Nothing, Exit[Status, Response]]
      )(new Service(_, _))
  }
}
