package scalapb.zio_grpc

import scalapb.zio_grpc.testservice.ZioTestservice.ZTestService
import zio.Has
import zio.clock.Clock
import zio.console.Console
import io.grpc.Status
import scalapb.zio_grpc.testservice.{Request, Response}
import zio.ZIO
import io.grpc.Status
import scalapb.zio_grpc.testservice.{Request, Response}
import zio.stream.ZStream
import io.grpc.ServerBuilder
import zio.test._

object BindableServiceSpec extends DefaultRunnableSpec {
  implicitly[ZBindableService[Any, ZTestService[Any, Has[RequestContext]]]]
  implicitly[ZBindableService[Any, ZTestService[Any, Has[SafeMetadata]]]]
  implicitly[ZBindableService[Any, ZTestService[Any, Any]]]

  implicitly[ZBindableService[Clock, ZTestService[Clock, Has[RequestContext]]]]
  implicitly[ZBindableService[Clock, ZTestService[Clock, Has[SafeMetadata]]]]
  implicitly[ZBindableService[Clock, ZTestService[Clock, Any]]]

  implicitly[ZBindableService[Clock with Console, ZTestService[Clock with Console, Has[RequestContext]]]]
  implicitly[ZBindableService[Clock with Console, ZTestService[Clock with Console, Has[SafeMetadata]]]]
  implicitly[ZBindableService[Clock with Console, ZTestService[Clock with Console, Any]]]

  class UnimpTestService[P, R, C] extends ZTestService[R, C] {
    def unary(request: Request): ZIO[R with C, Status, Response] = ???

    def serverStreaming(request: Request): ZStream[R with C, Status, Response] = ???

    def clientStreaming(request: zio.stream.Stream[Status, Request]): ZIO[R with C, Status, Response] = ???

    def bidiStreaming(request: zio.stream.Stream[Status, Request]): ZStream[R with C, Status, Response] = ???
  }

  object S1 extends UnimpTestService[Int, Any, Has[RequestContext]]
  object S2 extends UnimpTestService[Int, Any, Has[SafeMetadata]]
  object S3 extends UnimpTestService[Int, Any, Any]
  object S4 extends UnimpTestService[Int, Clock, Has[SafeMetadata]]
  object S5 extends UnimpTestService[Int, Clock, Has[RequestContext]]
  object S6 extends UnimpTestService[Int, Clock, Any]
  object S7 extends UnimpTestService[Int, Console, Any]

  ServerLayer.fromService(ServerBuilder.forPort(9000), S1)
  ServerLayer.fromService(ServerBuilder.forPort(9000), S2)
  ServerLayer.fromService(ServerBuilder.forPort(9000), S3)
  ServerLayer.fromService(ServerBuilder.forPort(9000), S4)
  ServerLayer.fromService(ServerBuilder.forPort(9000), S5)
  ServerLayer.fromService(ServerBuilder.forPort(9000), S6)
  ServerLayer.fromService(ServerBuilder.forPort(9000), S7)
  ServerLayer.fromServices(ServerBuilder.forPort(9000), S1, S2, S3)
  ServerLayer.fromServices(ServerBuilder.forPort(9000), S1, S4, S7)

  def spec = suite("BindableServiceSpec")()
}
