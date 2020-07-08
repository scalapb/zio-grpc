package scalapb.zio_grpc

import zio.Has

// Represents evidence that a service with Context C can be bound (that is, we can
// generate ServerServiceDefinition for it.  All it means is that there is a conversion
// from RequestContext to C.
trait CanBind[C] {
  def bind(in: RequestContext): C
}

object CanBind {
  implicit val canBindRC: CanBind[Has[RequestContext]] = Has(_)
  implicit val canBindMD: CanBind[Has[SafeMetadata]]   = t => Has(t.metadata)
  implicit val canBindAny: CanBind[Any]                = t => t
}
