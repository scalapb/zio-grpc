package scalapb.zio_grpc

import io.grpc.MethodDescriptor
import io.grpc.CallOptions
import io.grpc.Deadline
import java.util.concurrent.TimeUnit
import zio.Duration
import zio.UIO
import zio.ZIO

final case class ClientCallContext(
    method: MethodDescriptor[_, _],
    options: CallOptions,
    metadata: SafeMetadata
)

object ClientTransform {

  val identity: ClientTransform = GTransform.identity

  def mapCallOptions(f: CallOptions => CallOptions): ClientTransform =
    ZTransform[ClientCallContext, ClientCallContext] { context =>
      ZIO.succeed(context.copy(options = f(context.options)))
    }

  def mapMetadataZIO(f: SafeMetadata => UIO[SafeMetadata]): ClientTransform =
    ZTransform[ClientCallContext, ClientCallContext] { context =>
      f(context.metadata).map(metadata => context.copy(metadata = metadata))
    }

  // Returns new instance with the metadata set to the one provide
  def withMetadataZIO(metadata: UIO[SafeMetadata]): ClientTransform =
    mapMetadataZIO(_ => metadata)

  // Returns new instance with the call options set to the one provide
  def withCallOptions(callOptions: CallOptions): ClientTransform =
    mapCallOptions(_ => callOptions)

  // Updates the deadline on the existing call options (results in new copy of CallOptions)
  def withDeadline(deadline: Deadline): ClientTransform =
    mapCallOptions(_.withDeadline(deadline))

  // Updates the timeout on the existing call options (results in new copy of CallOptions)
  def withTimeout(duration: Duration): ClientTransform =
    mapCallOptions(_.withDeadlineAfter(duration.toNanos, TimeUnit.NANOSECONDS))

  // Updates the timeout on the existing call options (results in new copy of CallOptions)
  def withTimeoutMillis(millis: Long): ClientTransform =
    withTimeout(Duration.fromMillis(millis))
}
