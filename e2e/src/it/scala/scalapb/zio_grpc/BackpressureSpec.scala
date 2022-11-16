package scalapb.zio_grpc

import zio.test._
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.inprocess.InProcessChannelBuilder
import zio._
import scalapb.zio_grpc.testservice.ZioTestservice
import scalapb.zio_grpc.testservice.{Request, Response}
import zio.stream.ZStream
import io.grpc.Status

object BackpressureSpec extends ZIOSpecDefault {
  val server =
    ServerLayer.access[ZioTestservice.TestService](InProcessServerBuilder.forName("backpressure-test").directExecutor())

  val client =
    ZLayer.scoped[Server] {
      for {
        ss     <- ZIO.service[Server.Service]
        port   <- ss.port
        ch      = InProcessChannelBuilder.forName("backpressure-test").usePlaintext().directExecutor()
        client <- ZioTestservice.TestServiceClient.scoped(ZManagedChannel(ch))
      } yield client
    }

  val service =
    ZLayer.succeed {
      new ZioTestservice.TestService {
        val responses = ZStream.iterate(0)(_ + 1).map(i => Response(i.toString)).take(100)

        def bidiStreaming(request: zio.stream.Stream[Status, Request]): ZStream[Any with Any, Status, Response] =
          request.drain ++ responses

        def serverStreaming(request: Request): ZStream[Any with Any, Status, Response] =
          responses

        def clientStreaming(request: zio.stream.Stream[Status, Request]): ZIO[Any with Any, Status, Response] = ???

        def unary(request: Request): ZIO[Any with Any, Status, Response] = ???
      }
    }

  val spec =
    suite("BackpressureSpec")(
      test("Slow client") {
        for {
          _ <- ZioTestservice.TestServiceClient.serverStreaming(Request(Request.Scenario.OK)).foreach { response =>
                 ZIO.log(s"Received response: $response") *> Live.live(Clock.sleep(1.second))
               }
        } yield assertCompletes
      }
    ).provideSomeLayer((service >+> server) >>> client)
}
