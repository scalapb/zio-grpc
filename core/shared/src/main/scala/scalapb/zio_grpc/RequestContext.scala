package scalapb.zio_grpc

import io.grpc.{Attributes, MethodDescriptor, ServerCall}

final case class RequestContext(
    metadata: SafeMetadata,
    authority: Option[String],
    methodDescriptor: MethodDescriptor[_, _],
    attributes: Attributes,
    context: SafeContext
)

object RequestContext {
  def fromServerCall[Req, Res](
      metadata: SafeMetadata,
      sc: ServerCall[Req, Res],
      context: SafeContext
  ): RequestContext =
    RequestContext(
      metadata,
      Option(sc.getAuthority()),
      sc.getMethodDescriptor(),
      sc.getAttributes(),
      context
    )
}
