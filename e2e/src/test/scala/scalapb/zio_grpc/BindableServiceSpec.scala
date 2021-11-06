package scalapb.zio_grpc

import scalapb.zio_grpc.testservice.ZioTestservice.ZTestService
import zio.Has
import zio.Clock
import zio.Console
import io.grpc.Status
import scalapb.zio_grpc.testservice.{Request, Response}
import zio.ZIO
import zio.stream.ZStream
import io.grpc.ServerBuilder
import zio.test._

object BindableServiceSpec extends DefaultRunnableSpec {
  implicitly[ZBindableService[Any, ZTestService[Any, Has[RequestContext]]]]
  implicitly[ZBindableService[Any, ZTestService[Any, Has[SafeMetadata]]]]
  implicitly[ZBindableService[Any, ZTestService[Any, Any]]]

  implicitly[ZBindableService[Has[Clock], ZTestService[Has[Clock], Has[RequestContext]]]]
  implicitly[ZBindableService[Has[Clock], ZTestService[Has[Clock], Has[SafeMetadata]]]]
  implicitly[ZBindableService[Has[Clock], ZTestService[Has[Clock], Any]]]

  implicitly[
    ZBindableService[Has[Clock] with Has[Console], ZTestService[Has[Clock] with Has[Console], Has[RequestContext]]]
  ]
  implicitly[
    ZBindableService[Has[Clock] with Has[Console], ZTestService[Has[Clock] with Has[Console], Has[SafeMetadata]]]
  ]
  implicitly[ZBindableService[Has[Clock] with Has[Console], ZTestService[Has[Clock] with Has[Console], Any]]]

  class UnimpTestService[P, R, C] extends ZTestService[R, C] {
    def unary(request: Request): ZIO[R with C, Status, Response] = ???

    def serverStreaming(request: Request): ZStream[R with C, Status, Response] = ???

    def clientStreaming(request: zio.stream.ZStream[Any, Status, Request]): ZIO[R with C, Status, Response] = ???

    def bidiStreaming(request: zio.stream.ZStream[Any, Status, Request]): ZStream[R with C, Status, Response] = ???
  }

  object S1 extends UnimpTestService[Int, Any, Has[RequestContext]]
  object S2 extends UnimpTestService[Int, Any, Has[SafeMetadata]]
  object S3 extends UnimpTestService[Int, Any, Any]
  object S4 extends UnimpTestService[Int, Has[Clock], Has[SafeMetadata]]
  object S5 extends UnimpTestService[Int, Has[Clock], Has[RequestContext]]
  object S6 extends UnimpTestService[Int, Has[Clock], Any]
  object S7 extends UnimpTestService[Int, Has[Console], Any]

  ServerLayer.fromService(ServerBuilder.forPort(9000), S1)
  ServerLayer.fromService(ServerBuilder.forPort(9000), S2)
  ServerLayer.fromService(ServerBuilder.forPort(9000), S3)
  ServerLayer.fromService(ServerBuilder.forPort(9000), S4)
  ServerLayer.fromService(ServerBuilder.forPort(9000), S5)
  ServerLayer.fromService(ServerBuilder.forPort(9000), S6)
  ServerLayer.fromService(ServerBuilder.forPort(9000), S7)
  val l3a = ServerLayer.fromServices(ServerBuilder.forPort(9000), S1, S2, S3)
  val l3b = ServerLayer.fromServices(ServerBuilder.forPort(9000), S1, S4, S7)

  val y1 = ServiceList.add(S1)
  val y2 = ServiceList.add(S2)
  val y3 = ServiceList.add(S3)
  val y4 = ServiceList.add(S4)
  val y5 = ServiceList.add(S5)
  val y6 = ServiceList.add(S6)
  val y7 = ServiceList.add(S7)
  val z1 = ServiceList.addM(ZIO.succeed(S1))
  val z2 = ServiceList.addM(ZIO.succeed(S2))
  val z3 = ServiceList.addM(ZIO.succeed(S3))
  val z4 = ServiceList.addM(ZIO.succeed(S4))
  val z5 = ServiceList.addM(ZIO.succeed(S5))
  val z6 = ServiceList.addM(ZIO.succeed(S6))
  val z7 = ServiceList.addM(ZIO.succeed(S7))
  val z8 = ServiceList.access[S1.type]

  def spec: ZSpec[ZTestEnv, scalapb.zio_grpc.BindableServiceSpec.Failure] = suite("BindableServiceSpec")(
    test("empty - required to make the compiler happy") {
      assertCompletes
    }
  )
}
