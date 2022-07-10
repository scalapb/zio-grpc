package examples

import io.grpc.ManagedChannelBuilder
import examples.greeter.ZioGreeter.GreeterClient
import examples.greeter._
import zio.Console._
import scalapb.zio_grpc.ZManagedChannel
import io.grpc.Channel
import zio._

object ExampleClient extends zio.ZIOAppDefault {
  final def run =
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
      .provideLayer(clientLayer)
}
