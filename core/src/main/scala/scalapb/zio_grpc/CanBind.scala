package scalapb.zio_grpc

// Represents evidence that a service with Context C can be bound (that is, we can
// generate a ServerServiceDefinition for it). To be able to bind a service with Context C
// we need to be able to convert a RequestContext to C.
trait CanBind[C] {
  def bind(in: RequestContext): C
}

trait CanBindLowPriority {
  implicit val canBindRC: CanBind[RequestContext] = identity(_)
  implicit val canBindMD: CanBind[SafeMetadata]   = t => t.metadata
}

object CanBind extends CanBindLowPriority {
  implicit val canBindAny: CanBind[Any] = identity(_)
}
