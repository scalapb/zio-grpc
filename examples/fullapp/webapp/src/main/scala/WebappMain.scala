package examples

import scalapb.grpc.Channels
import examples.greeter.ZioGreeter.GreeterClient
import zio._
import zio.Console._
import examples.greeter.Request
import scalapb.zio_grpc.ZManagedChannel
import io.grpc.Status

object WebappMain extends ZIOAppDefault {
  val clientLayer = GreeterClient.live(
    ZManagedChannel(Channels.grpcwebChannel("http://localhost:8080"))
  )

  val appLogic =
    printLine("Hello!") *>
      GreeterClient
        .greet(Request("Foo!"))
        .foldZIO(s => printLine(s"error: $s"), s => print(s"success: $s")) *>
      (GreeterClient
        .points(Request("Foo!"))
        .foreach(s => printLine(s"success: $s").orDie)
        .catchAll { (s: Status) =>
          printLine(s"Caught: $s")
        })

  def run =
    (appLogic.provideLayer(zio.Console.live ++ clientLayer).ignore *> printLine(
      "Done"
    )).exitCode
}
