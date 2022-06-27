package scalapb.zio_grpc

import zio.URIO
import io.grpc.ServerServiceDefinition

trait ZGeneratedService[-R, -C, S[-_, -_]] {
  this: S[R, C] =>
}

trait GenericBindable[S[_, _]] {
  def bind[R](s: S[R, RequestContext]): URIO[R, ServerServiceDefinition]
}
