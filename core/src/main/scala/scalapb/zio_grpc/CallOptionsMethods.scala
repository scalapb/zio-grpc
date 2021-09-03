package scalapb.zio_grpc

import io.grpc.CallOptions
import io.grpc.Status
import java.util.concurrent.TimeUnit
import zio.ZIO
import zio.Duration
import io.grpc.Deadline

trait CallOptionsMethods[Repr] {
  def mapCallOptionsM(f: CallOptions => zio.IO[Status, CallOptions]): Repr

  def withCallOptions(callOptions: CallOptions): Repr = mapCallOptionsM(_ => ZIO.succeed(callOptions))
  def withDeadline(deadline: Deadline): Repr          = mapCallOptionsM(co => ZIO.succeed(co.withDeadline(deadline)))
  def withTimeout(duration: Duration): Repr           =
    mapCallOptionsM(co => ZIO.effectTotal(co.withDeadlineAfter(duration.toNanos, TimeUnit.NANOSECONDS)))
  def withTimeoutMillis(millis: Long): Repr           = withTimeout(Duration.fromMillis(millis))
}
