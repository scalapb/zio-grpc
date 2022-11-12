package scalapb.zio_grpc

import io.grpc.MethodDescriptor
import io.grpc.Attributes
import io.grpc.ServerCall
import zio.stm.TSemaphore

final case class RequestContext(
    metadata: SafeMetadata,
    responseMetadata: SafeMetadata,
    authority: Option[String],
    methodDescriptor: MethodDescriptor[_, _],
    attributes: Attributes,
    canSend: TSemaphore
)

object RequestContext {
  def fromServerCall[Req, Res](
      metadata: SafeMetadata,
      responseMetadata: SafeMetadata,
      canSend: TSemaphore,
      sc: ServerCall[Req, Res]
  ): RequestContext =
    RequestContext(
      metadata,
      responseMetadata,
      Option(sc.getAuthority()),
      sc.getMethodDescriptor(),
      sc.getAttributes(),
      canSend
    )
}
