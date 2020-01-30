package example

import io.grpc.ManagedChannelBuilder
import example.greeter._
import zio.console._
import scalapb.grpc.zio.ZManagedChannel

object ExampleClient extends zio.App {
  final def run(args: List[String]) =
    myAppLogic.fold({_ => 1 }, _ => 0)

  def env = {
    val builder = ManagedChannelBuilder.forAddress("localhost", 8080).usePlaintext()
    for {
      channel <- ZManagedChannel.make(builder)
    } yield new MyService with Console.Live {
      val myService = MyService.client(channel)
    }
  }

  def myAppLogic = (
    for {
      r <- MyService.>.greet(Request("Hello"))
      _ <- putStrLn(r.resp)
      f <- MyService.>.greet(Request("Bye"))
      _ <- putStrLn(f.resp)
    } yield ()).provideManaged(env)
}
