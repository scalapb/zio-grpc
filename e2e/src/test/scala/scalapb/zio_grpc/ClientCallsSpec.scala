package scalapb.zio_grpc

import io.grpc.{CallOptions, ManagedChannelBuilder}
import scalapb.zio_grpc.TestUtils._
import scalapb.zio_grpc.client.ClientCalls
import scalapb.zio_grpc.testservice._
import zio._
import zio.test._
import zio.test.Assertion._

object ClientCallsSpec extends ZIOSpecDefault {

  def unarySuite =
    suite("unaryCall")(
      test("should not fail with 'INTERNAL: already started' on retry") {
        for {
          channel <- ZChannel.scoepd[Any](
                       ManagedChannelBuilder.forAddress("localhost", 0).usePlaintext(),
                       Nil,
                       1.second
                     )
          meta    <- SafeMetadata.make
          res     <- ClientCalls
                       .unaryCall(
                         // This test does not require working server
                         channel,
                         scalapb.zio_grpc.testservice.TestServiceGrpc.METHOD_UNARY,
                         CallOptions.DEFAULT,
                         meta,
                         Request(Request.Scenario.DELAY, in = 12)
                       )
                       .retry(Schedule.recurs(2))
                       .exit

        } yield
        // There was a bug, when call.start was invoked multiple times, so this test was failing
        // with 'already started' instead of 'io exception'
        assert(res)(fails(hasDescription("io exception")))
      }
    )

  def spec =
    suite("ClientCallsSpec")(unarySuite)
}
