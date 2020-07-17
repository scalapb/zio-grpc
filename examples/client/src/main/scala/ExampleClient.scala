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
      _ <- putStrLn(r.resp)
      f <- GreeterClient.greet(Request("Bye"))
      _ <- putStrLn(f.resp)
    } yield ())
      .onError { c => putStrLn(c.prettyPrint) }
      .provideLayer(Console.live ++ clientLayer)
}
