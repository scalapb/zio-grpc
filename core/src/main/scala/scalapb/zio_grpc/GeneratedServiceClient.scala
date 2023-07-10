package scalapb.zio_grpc

import io.grpc.CallOptions
import io.grpc.Deadline
import zio.Duration
import zio.UIO

trait GeneratedServiceClient[S] {
  this: S =>

  def transform(t: ZTransform[ClientCallContext, ClientCallContext]): S

  def transform(t: Transform): S = transform(t.toGTransform[ClientCallContext])

  // Returns new instance with modified call options
  def mapCallOptions(f: CallOptions => CallOptions): S = transform(ClientTransform.mapCallOptions(f))

  // Returns new instance with modified metadata
  def mapMetadataZIO(f: SafeMetadata => UIO[SafeMetadata]): S = transform(ClientTransform.mapMetadataZIO(f))

  // Returns new instance with the metadata set to the one provide
  def withMetadataZIO(metadata: UIO[SafeMetadata]): S = transform((ClientTransform.withMetadataZIO(metadata)))

  // Returns new instance with the call options set to the one provide
  def withCallOptions(callOptions: CallOptions): S = transform(ClientTransform.withCallOptions(callOptions))

  // Updates the deadline on the existing call options (results in new copy of CallOptions)
  def withDeadline(deadline: Deadline): S = transform(ClientTransform.withDeadline(deadline))

  // Updates the timeout on the existing call options (results in new copy of CallOptions)
  def withTimeout(duration: Duration): S = transform(ClientTransform.withTimeout(duration))

  // Updates the timeout on the existing call options (results in new copy of CallOptions)
  def withTimeoutMillis(millis: Long): S = transform(ClientTransform.withTimeoutMillis(millis))
}
