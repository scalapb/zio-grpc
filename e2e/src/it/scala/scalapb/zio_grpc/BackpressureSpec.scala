package scalapb.zio_grpc

import io.grpc.Status
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import scalapb.zio_grpc.testservice.Request
import scalapb.zio_grpc.testservice.Response
import scalapb.zio_grpc.testservice.ZioTestservice
import zio._
import zio.duration._
import zio.stream.ZStream
import zio.test._
import zio.test.environment.Live

object BackpressureSpec extends DefaultRunnableSpec {
  val server =
    ServerLayer.access[ZioTestservice.TestService](InProcessServerBuilder.forName("backpressure-test").directExecutor())

  val client =
    ZLayer.fromManaged {
      for {
        ss     <- ZManaged.service[Server.Service]
        port   <- ss.port.toManaged_
        ch      = InProcessChannelBuilder.forName("backpressure-test").usePlaintext().directExecutor()
        client <- ZioTestservice.TestServiceClient.managed(ZManagedChannel(ch))
      } yield client
    }

  val service =
    ZLayer.succeed[ZioTestservice.TestService] {
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
      testM("Slow client") {
        for {
          _ <- ZioTestservice.TestServiceClient.serverStreaming(Request(Request.Scenario.OK)).foreach { response =>
                 zio.console.putStrLn(s"Received response: $response") *> Live.live(zio.clock.sleep(1.second))
               }
        } yield assertCompletes
      }
    ).provideCustomLayerShared((service >+> server >+> client).orDie)
}
