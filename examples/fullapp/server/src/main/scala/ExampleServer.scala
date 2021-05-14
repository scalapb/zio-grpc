package examples

import examples.greeter.ZioGreeter.Greeter
import examples.greeter._
import zio.clock
import zio.clock.Clock
import zio.console.Console
import zio.{App, Schedule, IO, ZIO}
import zio.console
import zio.duration._
import zio.stream.Stream
import io.grpc.ServerBuilder
import zio.blocking._
import zio.console._
import io.grpc.Status
import zio.Managed
import zio.stream.ZSink
import scalapb.zio_grpc.Server
import zio.Layer
import zio.ZLayer
import zio.Has
import zio.ZManaged
import scalapb.zio_grpc.ServerLayer

object GreeterService {
  type GreeterService = Has[Greeter]

  class LiveService(clock: Clock.Service) extends Greeter {
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

  val live: ZLayer[Clock, Nothing, GreeterService] =
    ZLayer.fromService(new LiveService(_))
}

object ExampleServer extends App {
  def serverWait: ZIO[Console with Clock, Throwable, Unit] =
    for {
      _ <- putStrLn("Server is running. Press Ctrl-C to stop.")
      _ <- (putStr(".") *> ZIO.sleep(1.second)).forever
    } yield ()

  def serverLive(port: Int): Layer[Throwable, Server] =
    Clock.live >>> GreeterService.live >>> ServerLayer.access[Greeter](
      ServerBuilder.forPort(port)
    )

  def run(args: List[String]) = myAppLogic.exitCode

  val myAppLogic =
    serverWait.provideLayer(serverLive(9090) ++ Console.live ++ Clock.live)
}
