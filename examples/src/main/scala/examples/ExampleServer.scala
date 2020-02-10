package examples

import examples.greeter.myService.MyService
import examples.greeter._
import zio.clock
import zio.clock.Clock
import zio.console.Console
import zio.{App, Schedule, ZIO}
import zio.console
import zio.duration._
import zio.stream.{Stream, ZStream}
import io.grpc.ServerBuilder
import zio.blocking._
import zio.console._
import io.grpc.Status
import zio.Managed
import zio.stream.ZSink
import scalapb.zio_grpc.Server
import zio.ZLayer
import zio.Has

object GreetService {
  object Live extends MyService.Service[Clock] {
    def greet(req: Request): ZIO[Clock, Status, Response] =
      clock.sleep(300.millis) *> zio.IO.succeed(
        Response(resp = "hello " + req.name)
      )

    def points(
        request: Request
    ): ZStream[Clock, Status, Point] =
      (Stream(Point(3, 4))
        .scheduleElements(Schedule.spaced(1000.millis))
        .forever
        .take(5) ++
        Stream.fail(
          Status.INTERNAL
            .withDescription("There was an error!")
            .withCause(new RuntimeException)
        ))

    def bidi(
        request: ZStream[Any, Status, Point]
    ): ZStream[Clock, Status, Response] = {
      val sink = ZSink.collectAllN[Point](3)
      request.aggregate(sink.map(r => Response(r.toString())))
    }
  }
}

object ExampleServer extends App {
  def serverWait =
    for {
      _ <- putStrLn("Server is running. Press Ctrl-C to stop.")
      _ <- (putStr(".") *> ZIO.sleep(1.second)).forever
    } yield ()

  def runServer(
      port: Int
  ): ZIO[Clock with Blocking with Console, Throwable, Unit] = {
    for {
      rts <- ZIO.runtime[Clock with Console]
      builder = ServerBuilder
        .forPort(port)
        .addService(MyService.bindService(rts, GreetService.Live))
      server = Server.managed(builder).useForever
      _ <- server raceAttempt serverWait
    } yield ()
  }

  def run(args: List[String]) = myAppLogic.fold(_ => 1, _ => 0)

  val myAppLogic = runServer(8080)
}
