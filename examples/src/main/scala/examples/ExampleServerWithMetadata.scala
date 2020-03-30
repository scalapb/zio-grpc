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
import io.grpc.Metadata
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

object GreeterServiceWithMetadata {
  type GreeterServiceWithMetadata = Has[Greeter.WithMetadata]

  case class User(name: String)

  // Each request gets a User as a context parameter.
  class LiveService(clock: Clock.Service) extends Greeter.WithContext[User] {
    def greet(req: Request, user: User): IO[Status, Response] =
      IO.succeed(Response(s"Hello ${user.name}, req: ${req}"))

    def points(request: Request, user: User): Stream[Status, Point] = ???

    def bidi(
        request: Stream[Status, Point],
        user: User
    ): Stream[Status, Response] = ???
  }

  val UserKey =
    Metadata.Key.of("user-key", io.grpc.Metadata.ASCII_STRING_MARSHALLER)

  // Imagine this resolves an authenticated User instance from the Metadata.
  def findUser(metadata: Metadata): IO[Status, User] =
    Option(metadata.get(UserKey)) match {
      case Some(name) => IO.succeed(User(name))
      case _          => IO.fail(Status.UNAUTHENTICATED.withDescription("No access!"))
    }

  val live: ZLayer[Clock, Nothing, GreeterServiceWithMetadata] =
    ZLayer.fromService { c: Clock.Service =>
      Greeter.transformContext(new LiveService(c), findUser(_))
    }
}

object ExampleServerWithMetadata extends App {
  def serverWait: ZIO[Console with Clock, Throwable, Unit] =
    for {
      _ <- putStrLn("Server is running. Press Ctrl-C to stop.")
      _ <- (putStr(".") *> ZIO.sleep(1.second)).forever
    } yield ()

  def serverLive(port: Int): Layer[Nothing, Server] =
    Clock.live >>> GreeterServiceWithMetadata.live >>> Server
      .live[Greeter.WithContext[Metadata]](
        ServerBuilder.forPort(port)
      )

  def run(args: List[String]) = myAppLogic.fold(_ => 1, _ => 0)

  val myAppLogic =
    serverWait.provideLayer(serverLive(8080) ++ Console.live ++ Clock.live)
}
