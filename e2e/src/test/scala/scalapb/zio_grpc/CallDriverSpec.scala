package scalapb.zio_grpc

import io.grpc.Status
import scalapb.zio_grpc.TestUtils._
import scalapb.zio_grpc.server.CallDriver
import zio._
import zio.Clock._
import zio.Random._
import zio.test._

object CallDriverSpec extends ZIOSpecDefault {

  def spec = suite("CallDriverSpec")(
    test("exitToStatus prioritizes failures over interrupts") {
      val effectWithInterruptAndFailure = ZIO
        .foreachParDiscard(List.range(0, 16))(i =>
          for {
            delay <- nextIntBetween(100, 200)
            _     <- ClockLive.sleep(delay.milliseconds)
            _     <- ZIO.fail(Status.INVALID_ARGUMENT.withDescription(s"i=$i"))
          } yield ()
        )
        .withParallelism(128)
      assertM(effectWithInterruptAndFailure.exit map CallDriver.exitToStatus)(
        hasStatusCode(Status.INVALID_ARGUMENT)
      )
    }
  )

}
