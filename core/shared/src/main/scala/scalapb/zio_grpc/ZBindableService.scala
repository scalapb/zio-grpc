package scalapb.zio_grpc

import io.grpc.ServerServiceDefinition
import zio.URIO

/** Provides a way to bind a ZIO gRPC service implementations to a server. */
trait ZBindableService[-S] {
  type R

  /** Effectfully returns a {{io.grpc.ServerServiceDefinition}} for the given service instance */
  def bindService(serviceImpl: S): URIO[R, ServerServiceDefinition]
}

object ZBindableService {
  type Aux[S, R0] = ZBindableService[S] {
    type R = R0
  }
  def apply[S: ZBindableService] = implicitly[ZBindableService[S]]

  def serviceDefinition[S, R](
      serviceImpl: S
  )(implicit aux: Aux[S, R]): URIO[R, ServerServiceDefinition] =
    aux.bindService(serviceImpl)
}
