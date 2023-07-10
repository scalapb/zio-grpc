package scalapb.zio_grpc

import io.grpc.MethodDescriptor
import io.grpc.CallOptions

final case class ClientCallContext(
    method: MethodDescriptor[_, _],
    options: CallOptions,
    metadata: SafeMetadata
)
