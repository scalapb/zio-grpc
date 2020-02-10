package examples

import io.grpc.ManagedChannelBuilder
import examples.greeter.myService.MyService
import examples.greeter._
import zio.console._
import scalapb.zio_grpc.ZManagedChannel
import zio.Has
import io.grpc.Channel
import zio.ZLayer

object ExampleClient extends zio.App {
  final def run(args: List[String]) =
    myAppLogic.fold({_ => 1 }, _ => 0)

  def env = ZLayer.fromManaged {
    val builder = ManagedChannelBuilder.forAddress("localhost", 8080).usePlaintext()
    ZManagedChannel.make(builder).map(MyService.clientService(_))
  }

  def myAppLogic = (
    for {
      r <- MyService.greet(Request("Hello"))
      _ <- putStrLn(r.resp)
      f <- MyService.greet(Request("Bye"))
      _ <- putStrLn(f.resp)
    } yield ()).provideLayer(env ++ Console.live)
}
