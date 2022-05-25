package examples

import examples.greeter.ZioGreeter.Greeter
import examples.greeter._
import zio.Duration._
import zio.stream.{Stream, ZStream}
import io.grpc.ServerBuilder
import io.grpc.Status
import zio._
import zio.stream.ZSink
import scalapb.zio_grpc.Server
import scalapb.zio_grpc.ServerLayer
import zio.Console.{print, printLine}

object GreeterService {
  class LiveService(clock: Clock) extends Greeter {
    def greet(req: Request): IO[Status, Response] =
      clock.sleep(300.millis).as(Response(resp = "hello " + req.name))

    def points(
        request: Request
    ): Stream[Status, Point] =
      (ZStream(Point(3, 4))
        .schedule(Schedule.spaced(1000.millis))
        .forever
        .take(5) ++
        ZStream.fail(
          Status.INTERNAL
            .withDescription("There was an error!")
            .withCause(new RuntimeException)
        )).provideEnvironment(ZEnvironment(clock))

    def bidi(
        request: Stream[Status, Point]
    ): Stream[Status, Response] = {
      request.grouped(3).map(r => Response(r.toString()))
    }
  }

  val live: ZLayer[Clock, Nothing, Greeter] =
    ZLayer.fromFunction(new LiveService(_))
}

object ExampleServer extends ZIOAppDefault {
  def serverWait: ZIO[Any, Throwable, Unit] =
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
    serverWait.provideLayer(serverLive(9090))
}
