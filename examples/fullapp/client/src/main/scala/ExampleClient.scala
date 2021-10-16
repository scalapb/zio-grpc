package examples

import io.grpc.ManagedChannelBuilder
import examples.greeter.ZioGreeter.GreeterClient
import examples.greeter._
import zio.console._
import scalapb.zio_grpc.ZManagedChannel
import zio.Has
import io.grpc.Channel
import zio.Layer
import zio.ZLayer
import zio.Console.printLine

object ExampleClient extends zio.App {
  final def run(args: List[String]) =
    myAppLogic.exitCode

  def clientLayer: Layer[Throwable, GreeterClient] =
    GreeterClient.live(
      ZManagedChannel(
        ManagedChannelBuilder.forAddress("localhost", 9090).usePlaintext()
      )
    )

  def myAppLogic =
    (for {
      r <- GreeterClient.greet(Request("Hello"))
      _ <- printLine(r.resp)
      f <- GreeterClient.greet(Request("Bye"))
      _ <- printLine(f.resp)
    } yield ())
      .onError { c => printLine(c.prettyPrint).orDie }
      .provideLayer(Console.live ++ clientLayer)
}
