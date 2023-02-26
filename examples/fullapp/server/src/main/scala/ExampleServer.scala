package examples

import examples.greeter.ZioGreeter.Greeter
import examples.greeter._
import zio.Duration._
import zio.stream.{Stream, ZStream}
import io.grpc.ServerBuilder
import io.grpc.Status
import zio._
import scalapb.zio_grpc.{ServerMain, ServiceList}
import zio.Console.{print, printLine}

object GreeterService {
  object LiveService extends Greeter {
    def greet(req: Request): IO[Status, Response] =
      Clock.sleep(300.millis).as(Response(resp = "hello " + req.name))

    def points(
        request: Request
    ): Stream[Status, Point] =
      ZStream(Point(3, 4))
        .schedule(Schedule.spaced(1000.millis))
        .forever
        .take(5) ++
        ZStream.fail(
          Status.INTERNAL
            .withDescription("There was an error!")
            .withCause(new RuntimeException)
        )

    def bidi(
        request: Stream[Status, Point]
    ): Stream[Status, Response] =
      request.grouped(3).map(r => Response(r.toString()))
  }
}

object ExampleServer extends ServerMain {
  def services = ServiceList.add(GreeterService.LiveService)
}
