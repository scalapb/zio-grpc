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

object GreeterServiceWithMetadataPeek {
  type GreeterServiceWithMetadataPeek = Has[Greeter.WithMetadata]

  val UserKey =
    Metadata.Key.of("user-key", io.grpc.Metadata.ASCII_STRING_MARSHALLER)

  def verifyMetadata(metadata: Metadata): IO[Status, Unit] =
    Option(metadata.get(UserKey)) match {
      case Some("the-user") => IO.unit
      case _                => IO.fail(Status.UNAUTHENTICATED.withDescription("No access!"))
    }

  val live: ZLayer[Clock, Nothing, GreeterServiceWithMetadataPeek] =
    ZLayer.fromService((c: Clock.Service) =>
      Greeter.transformContext(
        new GreeterService.LiveService(c),
        verifyMetadata(_)
      )
    )
}

object ExampleServerWithMetadataPeek extends App {
  def serverWait: ZIO[Console with Clock, Throwable, Unit] =
    for {
      _ <- putStrLn("Server is running. Press Ctrl-C to stop.")
      _ <- (putStr(".") *> ZIO.sleep(1.second)).forever
    } yield ()

  def serverLive(port: Int): Layer[Nothing, Server] =
    Clock.live >>> GreeterServiceWithMetadataPeek.live >>> Server
      .live[Greeter.WithMetadata](
        ServerBuilder.forPort(port)
      )

  def run(args: List[String]) = myAppLogic.fold(_ => 1, _ => 0)

  val myAppLogic =
    serverWait.provideLayer(serverLive(8080) ++ Console.live ++ Clock.live)
}
