package scalapb

import io.grpc.Status
import zio.{IO, Scope, ZIO}
import zio.stream.Stream

package object zio_grpc {
  type GIO[A] = IO[Status, A]

  type GStream[A] = Stream[Status, A]

  type ZManagedChannel = ZIO[Scope, Throwable, ZChannel]

  @deprecated("Use ScopedServer instead of ManagedServer", "0.6.0")
  val ManagedServer = scalapb.zio_grpc.ScopedServer
}
