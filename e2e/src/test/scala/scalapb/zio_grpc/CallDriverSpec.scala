package scalapb.zio_grpc

import io.grpc.Status
import scalapb.zio_grpc.TestUtils._
import scalapb.zio_grpc.server.CallDriver
import zio._
import zio.duration._
import zio.test.{DefaultRunnableSpec, _}

object CallDriverSpec extends DefaultRunnableSpec {

  def spec = suite("CallDriverSpec")(
    testM("exitToStatus prioritizes failures over interrupts") {
      val effectWithInterruptAndFailure = ZIO
        .foreachParN_(128)(List.range(0, 16))(i =>
          for {
            delay <- random.nextIntBetween(100, 200)
            _     <- environment.live(clock.sleep(delay.milliseconds))
            _     <- ZIO.fail(Status.INVALID_ARGUMENT.withDescription(s"i=$i"))
          } yield ()
        )
      assertM(effectWithInterruptAndFailure.run map CallDriver.exitToStatus)(
        hasStatusCode(Status.INVALID_ARGUMENT)
      )
    }
  )

}
