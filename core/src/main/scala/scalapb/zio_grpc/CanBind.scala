package scalapb.zio_grpc

import zio.Has

// Represents evidence that a service with Context C can be bound (that is, we can
// generate a ServerServiceDefinition for it). To be able to bind a service with Context C
// we need to be able to convert a RequestContext to C.
trait CanBind[C] {
  def bind(in: Has[RequestContext]): C
}

trait CanBindLowPriority {
  implicit val canBindRC: CanBind[Has[RequestContext]] = identity
  implicit val canBindMD: CanBind[Has[SafeMetadata]]   = t => Has(t.get.metadata)
}

object CanBind extends CanBindLowPriority {
  implicit val canBindAny: CanBind[Any] = identity
}
