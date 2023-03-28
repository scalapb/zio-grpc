package examples

import examples.greeter.ZioGreeter.Greeter
import examples.greeter._
import zio.Duration._
import zio.stream.{Stream, ZStream}
import io.grpc.ServerBuilder
import io.grpc.{Status, StatusException}
import zio._
import scalapb.zio_grpc.{ServerMain, ServiceList}
import zio.Console.{print, printLine}

object GreeterService {
  object LiveService extends Greeter {
    def greet(req: Request): IO[StatusException, Response] =
      Clock.sleep(300.millis).as(Response(resp = "hello " + req.name))

    def points(
        request: Request
    ): Stream[StatusException, Point] =
      ZStream(Point(3, 4))
        .schedule(Schedule.spaced(1000.millis))
        .forever
        .take(5) ++
        ZStream.fail(
          Status.INTERNAL
            .withDescription(
              "There was an error! This error is for demonstration purposes and is expected."
            )
            .withCause(new RuntimeException)
            .asException
        )

    def bidi(
        request: Stream[StatusException, Point]
    ): Stream[StatusException, Response] =
      request.grouped(3).map(r => Response(r.toString()))
  }
}

object ExampleServer extends ServerMain {
  def services = ServiceList.add(GreeterService.LiveService)
}
