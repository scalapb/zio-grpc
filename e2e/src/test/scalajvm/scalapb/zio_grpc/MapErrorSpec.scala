package scalapb.zio_grpc

import scalapb.zio_grpc.testservice.ZioTestservice
import scalapb.zio_grpc.testservice.{Request, Response}
import zio.{IO, ZIO}
import zio.stream.{Stream, ZStream}
import zio.test.ZIOSpecDefault
import zio.test._
import zio.test.Assertion._
import ZioTestservice.GTestService
import io.grpc.{Status, StatusException}
import zio.stream.ZSink

case class CustomError(code: Int)

object MapErrorSpec extends ZIOSpecDefault {
  object ServerWithCustomError extends GTestService[Any, CustomError] {
    def unary(request: Request, context: Any): IO[CustomError, Response] = ZIO.fail(CustomError(request.in))

    def unaryTypeMapped(request: Request, context: Any): IO[CustomError, WrappedString] =
      ZIO.fail(CustomError(request.in))

    def serverStreaming(request: Request, context: Any): Stream[CustomError, Response] =
      ZStream(Response("1"), Response("2")) ++ ZStream.fail(CustomError(request.in))

    def serverStreamingTypeMapped(request: Request, context: Any): zio.stream.Stream[CustomError, WrappedString] =
      ZStream(WrappedString("1"), WrappedString("2")) ++ ZStream.fail(CustomError(request.in))

    def clientStreaming(request: zio.stream.Stream[StatusException, Request], context: Any): IO[CustomError, Response] =
      ???

    def bidiStreaming(
        request: zio.stream.Stream[StatusException, Request],
        context: Any
    ): zio.stream.Stream[CustomError, Response] = ???
  }

  val mappedServer = ServerWithCustomError.mapError(ce => new StatusException(Status.fromCodeValue(ce.code)))

  def spec = suite("MapErrorSpec")(
    test("maps errors for unary requests")(
      assertZIO(mappedServer.unary(Request(in = 4), 0).exit)(fails(TestUtils.hasStatusCode(Status.fromCodeValue(4))))
    ),
    test("maps errors for unary type mapped requests requests")(
      assertZIO(mappedServer.unaryTypeMapped(Request(in = 5), 0).exit)(
        fails(TestUtils.hasStatusCode(Status.fromCodeValue(5)))
      )
    ),
    test("maps errors for server streaming requests")(
      assertZIO(mappedServer.serverStreaming(Request(in = 6), 0).either.run(ZSink.collectAll))(
        hasAt(0)(isRight(equalTo(Response("1")))) &&
          hasAt(1)(isRight(equalTo(Response("2")))) &&
          hasAt(2)(isLeft(TestUtils.hasStatusCode(Status.fromCodeValue(6))))
      )
    ),
    test("maps errors for server streaming requests")(
      assertZIO(mappedServer.serverStreaming(Request(in = 6), 0).either.run(ZSink.collectAll))(
        hasAt(0)(isRight(equalTo(Response("1")))) &&
          hasAt(1)(isRight(equalTo(Response("2")))) &&
          hasAt(2)(isLeft(TestUtils.hasStatusCode(Status.fromCodeValue(6))))
      )
    ),
    test("maps errors for type mapped streaming requests")(
      assertZIO(mappedServer.serverStreamingTypeMapped(Request(in = 6), 0).either.run(ZSink.collectAll))(
        hasAt(0)(isRight(equalTo(WrappedString("1")))) &&
          hasAt(1)(isRight(equalTo(WrappedString("2")))) &&
          hasAt(2)(isLeft(TestUtils.hasStatusCode(Status.fromCodeValue(6))))
      )
    )
  )
}
