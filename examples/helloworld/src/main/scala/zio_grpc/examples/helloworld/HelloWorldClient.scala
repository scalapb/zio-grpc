package zio_grpc.examples.helloworld

import io.grpc.examples.helloworld.helloworld.ZioHelloworld.GreeterClient
import io.grpc.examples.helloworld.helloworld.HelloRequest
import io.grpc.ManagedChannelBuilder
import zio.Console._
import scalapb.zio_grpc.ZManagedChannel
import zio._

object HelloWorldClient extends zio.ZIOAppDefault {
  val clientLayer: Layer[Throwable, GreeterClient] =
    GreeterClient.live(
      ZManagedChannel(
        ManagedChannelBuilder.forAddress("localhost", 9000).usePlaintext()
      )
    )

  def myAppLogic =
    for {
      r <- GreeterClient.sayHello(HelloRequest("World"))
      _ <- printLine(r.message)
    } yield ()

  final def run =
    myAppLogic.provideCustomLayer(clientLayer).exitCode
}
