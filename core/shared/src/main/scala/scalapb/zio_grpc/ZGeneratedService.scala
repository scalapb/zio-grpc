package scalapb.zio_grpc

import zio.Has
import zio.URIO
import io.grpc.ServerServiceDefinition

trait ZGeneratedService[-R, -C, S[-_, -_]] {
  this: S[R, C] =>
}

trait GenericBindable[S[_, _]] {
  def bind[R, C](s: S[R, C], env: Has[RequestContext] => R with C): URIO[R, ServerServiceDefinition]
}
