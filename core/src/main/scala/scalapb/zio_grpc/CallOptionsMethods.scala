package scalapb.zio_grpc

import io.grpc.CallOptions
import io.grpc.Status
import java.util.concurrent.TimeUnit
import zio.ZIO
import zio.Duration
import io.grpc.Deadline

trait CallOptionsMethods[Repr] {
  def mapCallOptionsZIO(f: CallOptions => zio.IO[Status, CallOptions]): Repr

  def withCallOptions(callOptions: CallOptions): Repr = mapCallOptionsZIO(_ => ZIO.succeed(callOptions))
  def withDeadline(deadline: Deadline): Repr          = mapCallOptionsZIO(co => ZIO.succeed(co.withDeadline(deadline)))
  def withTimeout(duration: Duration): Repr           =
    mapCallOptionsZIO(co => ZIO.succeed(co.withDeadlineAfter(duration.toNanos, TimeUnit.NANOSECONDS)))
  def withTimeoutMillis(millis: Long): Repr           = withTimeout(Duration.fromMillis(millis))
}
