package examples

import scalapb.grpc.Channels
import examples.greeter.ZioGreeter.GreeterClient
import zio._
import zio.Console._
import examples.greeter.Request
import scalapb.zio_grpc.ZManagedChannel
import io.grpc.StatusException
import scalapb.zio_grpc.SafeMetadata

object WebappMain extends ZIOAppDefault {
  val clientLayer = GreeterClient.live(
    ZManagedChannel(Channels.grpcwebChannel("http://localhost:8080"))
  )

  val appLogic =
    printLine("Welcome to zio-grpc full app client!") *>
      printLine("Test 1: Successful unary request") *>
      GreeterClient
        .greet(Request("Foo!"))
        .foldZIO(
          s => printLine(s"error: $s"),
          s => printLine(s"success: $s")
        ) *>
      printLine("Test 2: Unary request that fails") *>
      GreeterClient
        .withMetadataZIO(SafeMetadata.make("serve-error" -> "true"))
        .greet(Request("Foo!"))
        .foldZIO(
          s => printLine(s"error: $s"),
          s => printLine(s"success: $s")
        ) *>
      printLine("Test 3: Server-streaing request") *>
      (GreeterClient
        .points(Request("Foo!"))
        .foreach(s => printLine(s"success: $s").orDie)
        .catchAll { (s: StatusException) =>
          printLine(s"Caught: $s")
        }) *>
      printLine("Test 4: Server-streaing request that fails") *>
      (GreeterClient
        .withMetadataZIO(SafeMetadata.make("serve-error" -> "true"))
        .points(Request("Foo!"))
        .foreach(s => printLine(s"success: $s").orDie)
        .catchAll { (s: StatusException) =>
          printLine(s"Caught: $s")
        })

  def run =
    (appLogic.provideLayer(clientLayer).ignore *> printLine(
      "Done"
    )).exitCode
}
