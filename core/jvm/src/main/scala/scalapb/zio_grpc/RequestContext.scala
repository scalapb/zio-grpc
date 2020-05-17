package scalapb.zio_grpc

import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Attributes
import io.grpc.ServerCall

final case class RequestContext(
    metadata: Metadata,
    authority: Option[String],
    methodDescriptor: MethodDescriptor[_, _],
    attributes: Attributes
)

object RequestContext {
  def fromServerCall[Req, Res](
      metadata: Metadata,
      sc: ServerCall[Req, Res]
  ): RequestContext =
    RequestContext(
      metadata,
      Option(sc.getAuthority()),
      sc.getMethodDescriptor(),
      sc.getAttributes()
    )
}
