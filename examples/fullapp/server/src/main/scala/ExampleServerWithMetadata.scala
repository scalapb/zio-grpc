package examples

import examples.greeter.ZioGreeter.{Greeter, ZGreeter}
import examples.greeter._
import zio._
import zio.stream.Stream
import io.grpc.Metadata
import io.grpc.ServerBuilder
import zio.Console._
import io.grpc.Status
import scalapb.zio_grpc.Server
import scalapb.zio_grpc.SafeMetadata
import scalapb.zio_grpc.RequestContext
import scalapb.zio_grpc.ServerLayer

object GreeterServiceWithMetadata {
  case class User(name: String)

  // Each request gets a User as a context parameter.
  class LiveService(clock: Clock) extends ZGreeter[Any, User] {
    def greet(req: Request): ZIO[User, Status, Response] =
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

  val live: ZLayer[Clock, Nothing, ZGreeter[Any, RequestContext]] = {
    c: Clock =>
      new LiveService(c).transformContextM(findUser(_))
  }.toLayer
}

object ExampleServerWithMetadata extends ZIOAppDefault {

  def serverWait: ZIO[Any, Throwable, Unit] =
    for {
      _ <- printLine("Server is running. Press Ctrl-C to stop.")
      _ <- (print(".") *> ZIO.sleep(1.second)).forever
    } yield ()

  def serverLive(port: Int): Layer[Throwable, Server] =
    ServerLayer.fromServiceLayer(
      ServerBuilder.forPort(port)
    )(Clock.live >>> GreeterServiceWithMetadata.live)

  def run = myAppLogic.exitCode

  val myAppLogic =
    serverWait.provideLayer(serverLive(8080))
}
