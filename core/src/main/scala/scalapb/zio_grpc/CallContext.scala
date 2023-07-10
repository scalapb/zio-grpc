package scalapb.zio_grpc

import io.grpc.MethodDescriptor
import io.grpc.CallOptions

import zio.UIO

final case class CallContext(
    method: MethodDescriptor[_, _],
    options: CallOptions,
    metadata: UIO[SafeMetadata]
)
