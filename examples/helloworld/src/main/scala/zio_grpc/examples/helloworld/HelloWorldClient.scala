package zio_grpc.examples.helloworld

import io.grpc.examples.helloworld.helloworld.ZioHelloworld.GreeterClient
import io.grpc.examples.helloworld.helloworld.HelloRequest
import io.grpc.ManagedChannelBuilder
import zio.console._
import scalapb.zio_grpc.ZManagedChannel
import zio.Layer

object HelloWorldClient extends zio.App {
  val clientLayer: Layer[Throwable, GreeterClient] =
    GreeterClient.live(
      ZManagedChannel(
        ManagedChannelBuilder.forAddress("localhost", 9000).usePlaintext()
      )
    )

  def myAppLogic =
    for {
      r <- GreeterClient.sayHello(HelloRequest("World"))
      _ <- putStrLn(r.message)
    } yield ()

  final def run(args: List[String]) =
    myAppLogic.provideCustomLayer(clientLayer).exitCode
}
