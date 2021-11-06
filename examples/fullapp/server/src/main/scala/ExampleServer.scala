package examplespp

import examples.greeter.ZioGreeter.Greeter
import examples.greeter._
import zio.Clock
import zio.Console
import zio.{ZIOAppDefault, Schedule, IO, ZIO}
import zio.Duration._
import zio.stream.Stream
import io.grpc.ServerBuilder
import zio.Blocking._
import io.grpc.Status
import zio.Managed
import zio.stream.ZSink
import scalapb.zio_grpc.Server
import zio.Layer
import zio.ZLayer
import zio.Has
import zio.ZManaged
import scalapb.zio_grpc.ServerLayer
import zio.Console.{print, printLine}

object GreeterService {
  type GreeterService = Has[Greeter]

  class LiveService(clock: Clock) extends Greeter {
    def greet(req: Request): IO[Status, Response] =
      clock.sleep(300.millis) *> zio.IO.succeed(
        Response(resp = "hello " + req.name)
      )

    def points(
        request: Request
    ): Stream[Status, Point] =
      (Stream(Point(3, 4))
        .schedule(Schedule.spaced(1000.millis))
        .forever
        .take(5) ++
        Stream.fail(
          Status.INTERNAL
            .withDescription("There was an error!")
            .withCause(new RuntimeException)
        )).provide(Has(clock))

    def bidi(
        request: Stream[Status, Point]
    ): Stream[Status, Response] = {
      request.grouped(3).map(r => Response(r.toString()))
    }
  }

  val live: ZLayer[Has[Clock], Nothing, GreeterService] =
    ZLayer.fromService(new LiveService(_))
}

object ExampleServer extends ZIOAppDefault {
  def serverWait: ZIO[Has[Console] with Has[Clock], Throwable, Unit] =
    for {
      _ <- printLine("Server is running. Press Ctrl-C to stop.")
      _ <- (print(".") *> ZIO.sleep(1.second)).forever
    } yield ()

  def serverLive(port: Int): Layer[Throwable, Server] =
    Clock.live >>> GreeterService.live >>> ServerLayer.access[Greeter](
      ServerBuilder.forPort(port)
    )

  def run = myAppLogic.exitCode

  val myAppLogic =
    serverWait.provideLayer(serverLive(9090) ++ Console.live ++ Clock.live)
}
