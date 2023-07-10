package scalapb

import io.grpc.StatusException
import zio.stream.Stream
import zio.{IO, Scope, ZIO}

package object zio_grpc {
  type GIO[A] = IO[StatusException, A]

  type GStream[A] = Stream[StatusException, A]

  type ZManagedChannel = ZIO[Scope, Throwable, ZChannel]

  @deprecated("Use ScopedServer instead of ManagedServer", "0.6.0")
  val ManagedServer = scalapb.zio_grpc.ScopedServer

  type ZTransform[ContextIn, ContextOut] = GTransform[ContextIn, StatusException, ContextOut, StatusException]
}
