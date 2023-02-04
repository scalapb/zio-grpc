package scalapb.zio_grpc

import zio.UIO
import io.grpc.ServerServiceDefinition

trait ZGeneratedService[-C, S[-_]] {
  this: S[C] =>
}

trait GeneratedService {
  type WithContext[-_]

  def withContext: WithContext[Any]
}

trait GenericBindable[-S] {
  def bind(s: S): UIO[ServerServiceDefinition]
}
