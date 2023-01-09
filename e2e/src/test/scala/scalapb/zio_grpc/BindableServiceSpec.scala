package scalapb.zio_grpc

import scalapb.zio_grpc.testservice.ZioTestservice.TestService
import scalapb.zio_grpc.testservice.ZioTestservice.ZTestService
import zio.ZIO
import io.grpc.StatusException
import scalapb.zio_grpc.testservice.{Request, Response}
import io.grpc.ServerBuilder
import zio.test._
import zio.IO
import zio.stream.Stream

object BindableServiceSpec extends ZIOSpecDefault {
  implicitly[ZBindableService[ZTestService[RequestContext]]]
  implicitly[ZBindableService[ZTestService[SafeMetadata]]]
  implicitly[ZBindableService[ZTestService[Any]]]
  implicitly[ZBindableService[TestService]]

  class UnimpTestService[C] extends ZTestService[C] {

    override def unary(request: Request, context: C): IO[StatusException, Response] = ???

    override def serverStreaming(request: Request, context: C): Stream[StatusException, Response] = ???

    override def clientStreaming(request: Stream[StatusException, Request], context: C): IO[StatusException, Response] = ???

    override def bidiStreaming(
        request: zio.stream.Stream[Status, Request],
        context: C
    ): ZStream[Status, StatusException, Response] = ???

  }

  object S1 extends UnimpTestService[RequestContext]
  object S2 extends UnimpTestService[SafeMetadata]
  object S3 extends UnimpTestService[Any]

  ServerLayer.fromService(ServerBuilder.forPort(9000), S1)
  ServerLayer.fromService(ServerBuilder.forPort(9000), S2)
  ServerLayer.fromService(ServerBuilder.forPort(9000), S3)

  val l1a = ServerLayer.fromService(ServerBuilder.forPort(9000), S1)
  val l2a = ServerLayer.fromServices(ServerBuilder.forPort(9000), S1, S2)
  val l3a = ServerLayer.fromServices(ServerBuilder.forPort(9000), S1, S2, S3)

  val y1 = ServiceList.add(S1)
  val y2 = ServiceList.add(S2)
  val y3 = ServiceList.add(S3)
  val z1 = ServiceList.addZIO(ZIO.succeed(S1))
  val z2 = ServiceList.addZIO(ZIO.succeed(S2))
  val z3 = ServiceList.addZIO(ZIO.succeed(S3))
  val z8 = ServiceList.addFromEnvironment[S1.type]

  def spec = suite("BindableServiceSpec")(
    test("empty - required to make the compiler happy") {
      assertCompletes
    }
  )
}
