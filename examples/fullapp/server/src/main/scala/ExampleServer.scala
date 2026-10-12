package examples

import examples.greeter.ZioGreeter.ZGreeter
import examples.greeter._
import zio.Duration._
import zio.stream.{Stream, ZStream}
import io.grpc.ServerBuilder
import io.grpc.{Status, StatusException}
import zio._
import scalapb.zio_grpc.{ServerMain, ServiceList}
import zio.Console.{print, printLine}
import io.grpc.Metadata
import scalapb.zio_grpc.SafeMetadata

// The metadata of each request will tell us if we want to serve an error
// back to the user.
case class RequestContext(serveError: Boolean)

object RequestContext {
  val ServeErrorKey =
    Metadata.Key.of("serve-error", Metadata.ASCII_STRING_MARSHALLER)

  def fromMetadata(md: SafeMetadata): UIO[RequestContext] = for {
    maybeValue <- md.get(ServeErrorKey)
    value = maybeValue.getOrElse("") match {
      case "1" | "true" => true
      case _            => false
    }
  } yield RequestContext(value)
}

object GreeterService extends ZGreeter[RequestContext] {
  def greet(
      req: Request,
      context: RequestContext
  ): IO[StatusException, Response] =
    if (context.serveError)
      ZIO.fail(
        Status.UNKNOWN
          .withDescription(
            "Error requested by setting serve-error to true (expected error)"
          )
          .asException
      )
    else
      Clock.sleep(300.millis).as(Response(resp = "hello " + req.name))

  def points(
      request: Request,
      context: RequestContext
  ): Stream[StatusException, Point] = {
    val res = ZStream(Point(3, 4))
      .schedule(Schedule.spaced(1000.millis))
      .forever
      .take(5)

    val fail = ZStream.fail(
      Status.INTERNAL
        .withDescription(
          "Error requested by setting serve-error to true (expected error)"
        )
        .withCause(new RuntimeException)
        .asException
    )

    if (context.serveError) res ++ fail else res
  }

  def bidi(
      request: Stream[StatusException, Point],
      context: RequestContext
  ): Stream[StatusException, Response] =
    request.grouped(3).map(r => Response(r.toString()))
}

object ExampleServer extends ServerMain {
  def services = ServiceList.add(
    GreeterService.transformContextZIO(RequestContext.fromMetadata(_))
  )
}
