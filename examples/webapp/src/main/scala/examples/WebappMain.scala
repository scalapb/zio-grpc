package examples

import scalapb.grpc.Channels
import examples.greeter.ZioGreeter.GreeterClient
import zio.App
import zio.Managed
import zio.ZIO
import zio.console._
import examples.greeter.Request

object WebappMain extends App {
  val clientLayer = GreeterClient.live(
    Managed
      .fromEffect(ZIO.effect(Channels.grpcwebChannel("http://localhost:8080")))
  )

  val appLogic =
    putStrLn("Hello!") *>
      GreeterClient.greet(Request("Foo!"))
      .foldM(s => putStrLn(s"error: $s"), s => putStrLn(s"success: $s"))

  def run(args: List[String]) =
    (appLogic.provideLayer(Console.live ++ clientLayer).ignore *> putStrLn(
      "Done"
    )).as(0)
}
