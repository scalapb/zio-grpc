package scalapb.zio_grpc

import io.grpc.{CallOptions, ManagedChannelBuilder}
import scalapb.zio_grpc.TestUtils._
import scalapb.zio_grpc.client.ClientCalls
import scalapb.zio_grpc.testservice._
import zio.Schedule
import zio.test.Assertion._
import zio.test._
import zio._

object ClientCallsSpec extends DefaultRunnableSpec {

  def unarySuite =
    suite("unaryCall")(
      testM("should not fail with 'INTERNAL: already started' on retry") {
        ZIO.succeed(
          new ZChannel(
            ManagedChannelBuilder
              .forAddress("localhost", 0)
              .usePlaintext()
              .build(),
            Nil
          )
        ).bracket(_.shutdown().orDie, channel =>
          for {
            meta <- SafeMetadata.make
            res  <- ClientCalls
                      .unaryCall(
                        channel,
                        scalapb.zio_grpc.testservice.TestServiceGrpc.METHOD_UNARY,
                        CallOptions.DEFAULT,
                        meta,
                        Request(Request.Scenario.DELAY, in = 12)
                      )
                      .retry(Schedule.recurs(2))
                      .run

          } yield
          // There was a bug, when call.start was invoked multiple times, so this test was failing
          // with 'already started' instead of 'io exception'
          assert(res)(fails(hasDescription("io exception")))
        )
      }
    )

  def spec =
    suite("ClientCallsSpec")(unarySuite)
}
