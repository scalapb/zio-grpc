package scalapb.zio_grpc

import zio.UIO
import io.grpc.ServerServiceDefinition

/** Provides a way to bind a ZIO gRPC service implementations to a server. */
trait ZBindableService[-S] {

  /** Effectfully returns a {{io.grpc.ServerServiceDefinition}} for the given service instance */
  def bindService(serviceImpl: S): UIO[ServerServiceDefinition]
}
