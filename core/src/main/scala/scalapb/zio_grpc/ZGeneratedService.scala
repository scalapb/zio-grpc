package scalapb.zio_grpc

import zio.UIO
import io.grpc.ServerServiceDefinition

trait ZGeneratedService[-C, S[-_]] {
  this: S[C] =>
}

trait GenericBindable[S[_]] {
  def bind(s: S[RequestContext]): UIO[ServerServiceDefinition]
}
