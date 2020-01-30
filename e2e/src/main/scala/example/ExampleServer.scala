package example

import example.greeter._
import zio.clock.Clock
import zio.{App, Schedule, ZIO}
import zio.console._
import zio.duration._
import zio.stream.{Stream, ZStream}
import io.grpc.ServerBuilder
import zio.blocking._
import zio.console._
import io.grpc.Status
import io.grpc.Status
import zio.Managed
import zio.stream.ZSink

class Server(private val underlying: io.grpc.Server) {
  def serve() = effectBlocking {
    underlying.start()
    underlying.awaitTermination()
  }
}

object Server {
  def make(builder: ServerBuilder[_]): Managed[Throwable, io.grpc.Server] = {
    Managed.make(ZIO.effect(builder.build.start))(
      s => ZIO.effect(s.shutdown).ignore
    )
  }
}

object GreetService {
  trait Live extends MyService.Service[Clock] {
    def greet(req: Request): ZIO[Clock, Status, Response] =
      zio.clock.sleep(300.millis) *> zio.IO.succeed(
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
            .withDescription("Gazoomba")
            .withCause(new RuntimeException)
        ))

    def bidi(
        request: ZStream[Clock, Status, Point]
    ): ZStream[Clock, Status, Response] = {
      val zm = ZSink.collectAllN[Point](3)
      request.aggregate(zm.map(r => Response(r.toString())))
    }
  }

  object Live extends Live
}

object ExampleServer extends App {

  def serverWait =
    for {
      _ <- Console.Live.console
        .putStrLn("Server is running. Press Ctrl-C to stop.")
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
      server = Server.make(builder).useForever
      _ <- server raceAttempt serverWait
    } yield ()
  }

  def run(args: List[String]) = myAppLogic.fold(_ => 1, _ => 0)

  val myAppLogic = runServer(8080)
}
