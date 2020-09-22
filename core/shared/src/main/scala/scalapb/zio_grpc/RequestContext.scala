package scalapb.zio_grpc

import io.grpc.{Attributes, Context, MethodDescriptor, ServerCall}

final case class RequestContext(
    metadata: SafeMetadata,
    authority: Option[String],
    methodDescriptor: MethodDescriptor[_, _],
    attributes: Attributes,
    context: Context
)

object RequestContext {
  def fromServerCall[Req, Res](
      metadata: SafeMetadata,
      sc: ServerCall[Req, Res],
      context: Context
  ): RequestContext =
    RequestContext(
      metadata,
      Option(sc.getAuthority()),
      sc.getMethodDescriptor(),
      sc.getAttributes(),
      context
    )
}
