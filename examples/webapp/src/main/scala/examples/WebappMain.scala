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

object WebappMain extends App {
  val clientLayer = GreeterClient.live(
    ZManagedChannel(Channels.grpcwebChannel("http://localhost:8080"))
  )

  val appLogic =
    putStrLn("Hello!") *>
      GreeterClient
        .greet(Request("Foo!"))
        .foldM(s => putStrLn(s"error: $s"), s => putStrLn(s"success: $s")) *>
      (GreeterClient
        .points(Request("Foo!"))
        .foreach(s => putStrLn(s"success: $s"))
        .catchAll { (s: Status) =>
          putStrLn(s"Caught: $s")
        })

  def run(args: List[String]) =
    (appLogic.provideLayer(Console.live ++ clientLayer).ignore *> putStrLn(
      "Done"
    )).exitCode
}
