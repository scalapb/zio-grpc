package scalapb.zio_grpc

import zio.URIO
import io.grpc.ServerServiceDefinition

/** Provides a way to bind a ZIO gRPC service implementations to a server.
  *
  * S is a Service type with an implementation that depends on an environment of type R (i.e.,
  * the methods return ZIO[R, Status, _] or ZStream[R, Status, _])
  */
trait ZBindableService[-R, -S] {

  /** Effectfully returns a {{io.grpc.ServerServiceDefinition}} for the given service instance */
  def bindService(serviceImpl: S): URIO[R, ServerServiceDefinition]
}
