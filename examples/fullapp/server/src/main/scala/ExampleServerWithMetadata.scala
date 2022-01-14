package examples

import examples.greeter.ZioGreeter.{Greeter, ZGreeter}
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
import scalapb.zio_grpc.SafeMetadata
import scalapb.zio_grpc.RequestContext
import zio.URIO
import scalapb.zio_grpc.ServerLayer

object GreeterServiceWithMetadata {
  case class User(name: String)

  // Each request gets a User as a context parameter.
  class LiveService(clock: Clock.Service) extends ZGreeter[Any, Has[User]] {
    def greet(req: Request): ZIO[Has[User], Status, Response] =
      for {
        name <- ZIO.service[User].map(_.name)
      } yield Response(s"Hello ${name}, req: ${req}")

    def points(request: Request): Stream[Status, Point] = ???

    def bidi(
        request: Stream[Status, Point]
    ): Stream[Status, Response] = ???
  }

  val UserKey =
    Metadata.Key.of("user-key", io.grpc.Metadata.ASCII_STRING_MARSHALLER)

  // Imagine this resolves an authenticated User instance from the Metadata.
  def findUser(rc: RequestContext): IO[Status, User] =
    rc.metadata.get(UserKey).flatMap {
      case Some(name) => IO.succeed(User(name))
      case _          => IO.fail(Status.UNAUTHENTICATED.withDescription("No access!"))
    }

  val live: ZLayer[Clock, Nothing, Has[ZGreeter[Any, Has[RequestContext]]]] =
    ZLayer.fromService { c: Clock.Service =>
      new LiveService(c).transformContextM(findUser(_))
    }
}

object ExampleServerWithMetadata extends App {
  def serverWait: ZIO[Console with Clock, Throwable, Unit] =
    for {
      _ <- putStrLn("Server is running. Press Ctrl-C to stop.")
      _ <- (putStr(".") *> ZIO.sleep(1.second)).forever
    } yield ()

  def serverLive(port: Int): Layer[Throwable, Server] =
    ServerLayer.fromServiceLayer(
      ServerBuilder.forPort(port)
    )(Clock.live >>> GreeterServiceWithMetadata.live)

  def run(args: List[String]) = myAppLogic.exitCode

  val myAppLogic =
    serverWait.provideLayer(serverLive(8080) ++ Console.live ++ Clock.live)
}
