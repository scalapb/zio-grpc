package scalapb.zio_grpc

import zio.UIO

import io.grpc.MethodDescriptor
import io.grpc.Attributes
import io.grpc.ServerCall

final case class RequestContext(
    metadata: SafeMetadata,
    authority: Option[String],
    methodDescriptor: MethodDescriptor[_, _],
    attributes: Attributes,
    responseMetadata: SafeMetadata
)

object RequestContext {
  def fromServerCall[Req, Res](
      metadata: SafeMetadata,
      sc: ServerCall[Req, Res]
  ): UIO[RequestContext] =
    SafeMetadata.make.map {
      RequestContext(
        metadata,
        Option(sc.getAuthority()),
        sc.getMethodDescriptor(),
        sc.getAttributes(),
        _
      )
    }
}
