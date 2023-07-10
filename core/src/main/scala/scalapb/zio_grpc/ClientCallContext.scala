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

  def mapCallOptions(f: CallOptions => CallOptions): ZTransform[ClientCallContext, ClientCallContext] =
    ZTransform[ClientCallContext, ClientCallContext] { context =>
      ZIO.succeed(context.copy(options = f(context.options)))
    }

  def mapMetadataZIO(f: SafeMetadata => UIO[SafeMetadata]): ZTransform[ClientCallContext, ClientCallContext] =
    ZTransform[ClientCallContext, ClientCallContext] { context =>
      f(context.metadata).map(metadata => context.copy(metadata = metadata))
    }

  // Returns new instance with the metadata set to the one provide
  def withMetadataZIO(metadata: UIO[SafeMetadata]): ZTransform[ClientCallContext, ClientCallContext] =
    mapMetadataZIO(_ => metadata)

  // Returns new instance with the call options set to the one provide
  def withCallOptions(callOptions: CallOptions): ZTransform[ClientCallContext, ClientCallContext] =
    mapCallOptions(_ => callOptions)

  // Updates the deadline on the existing call options (results in new copy of CallOptions)
  def withDeadline(deadline: Deadline): ZTransform[ClientCallContext, ClientCallContext] =
    mapCallOptions(_.withDeadline(deadline))

  // Updates the timeout on the existing call options (results in new copy of CallOptions)
  def withTimeout(duration: Duration): ZTransform[ClientCallContext, ClientCallContext] =
    mapCallOptions(_.withDeadlineAfter(duration.toNanos, TimeUnit.NANOSECONDS))

  // Updates the timeout on the existing call options (results in new copy of CallOptions)
  def withTimeoutMillis(millis: Long): ZTransform[ClientCallContext, ClientCallContext] =
    withTimeout(Duration.fromMillis(millis))
}
