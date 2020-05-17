package scalapb.zio_grpc

import io.grpc.ServerServiceDefinition
import zio.URIO

/** Provides a way to bind a ZIO gRPC service implementations to a server. */
trait ZBindableService[-S] {
  self =>
  type R

  /** Effectfully returns a {{io.grpc.ServerServiceDefinition}} for the given service instance */
  def bindService(serviceImpl: S): URIO[R, ServerServiceDefinition]

  def transformM[R1 <: R, S1](
      f: S1 => URIO[R1, S]
  ): ZBindableService.Aux[S1, R1] =
    new ZBindableService[S1] {
      type R = R1
      def bindService(serviceImpl: S1): URIO[R, ServerServiceDefinition] =
        f(serviceImpl).flatMap(self.bindService)
    }

  def transform[S1](f: S1 => S): ZBindableService.Aux[S1, R] =
    transformM(s => URIO.succeed(f(s)))
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
