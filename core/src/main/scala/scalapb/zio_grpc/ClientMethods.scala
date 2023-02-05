package scalapb.zio_grpc

import io.grpc.CallOptions
import java.util.concurrent.TimeUnit
import zio.Duration
import zio.UIO
import io.grpc.Deadline

trait ClientMethods[Repr] {
  // Returns new instance with modified call options
  def mapCallOptions(f: CallOptions => CallOptions): Repr

  // Returns new instance with modified metadata
  def mapMetadataZIO(f: SafeMetadata => UIO[SafeMetadata]): Repr

  // Returns new instance with the metadata set to the one provide
  def withMetadataZIO(metadata: UIO[SafeMetadata]): Repr = mapMetadataZIO(_ => metadata)

  // Returns new instance with the call options set to the one provide
  def withCallOptions(callOptions: CallOptions): Repr = mapCallOptions(_ => callOptions)

  // Updates the deadline on the existing call options (results in new copy of CallOptions)
  def withDeadline(deadline: Deadline): Repr = mapCallOptions(_.withDeadline(deadline))

  // Updates the timeout on the existing call options (results in new copy of CallOptions)
  def withTimeout(duration: Duration): Repr =
    mapCallOptions(_.withDeadlineAfter(duration.toNanos, TimeUnit.NANOSECONDS))

  // Updates the timeout on the existing call options (results in new copy of CallOptions)
  def withTimeoutMillis(millis: Long): Repr = withTimeout(Duration.fromMillis(millis))
}

trait TransformableClient[Repr] {
  def transform(t: Transform): Repr
}
