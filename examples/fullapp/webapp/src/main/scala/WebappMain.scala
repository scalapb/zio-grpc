package examples

import scalapb.grpc.Channels
import examples.greeter.ZioGreeter.GreeterClient
import zio.App
import zio.Managed
import zio.ZIO
import zio.console._
import examples.greeter.Request
import scalapb.zio_grpc.ZManagedChannel
import io.grpc.Status
import zio.Console.{print, printLine}

object WebappMain extends App {
  val clientLayer = GreeterClient.live(
    ZManagedChannel(Channels.grpcwebChannel("http://localhost:8080"))
  )

  val appLogic =
    printLine("Hello!") *>
      GreeterClient
        .greet(Request("Foo!"))
        .foldM(s => printLine(s"error: $s"), s => print(s"success: $s")) *>
      (GreeterClient
        .points(Request("Foo!"))
        .foreach(s => printLine(s"success: $s").orDie)
        .catchAll { (s: Status) =>
          printLine(s"Caught: $s")
        })

  def run(args: List[String]) =
    (appLogic.provideLayer(Console.live ++ clientLayer).ignore *> printLine(
      "Done"
    )).exitCode
}
