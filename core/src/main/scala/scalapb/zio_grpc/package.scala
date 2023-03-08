package scalapb

import io.grpc.StatusRuntimeException
import zio.stream.Stream
import zio.{IO, Scope, ZIO}

package object zio_grpc {
  type GIO[A] = IO[StatusRuntimeException, A]

  type GStream[A] = Stream[StatusRuntimeException, A]

  type ZManagedChannel = ZIO[Scope, Throwable, ZChannel]

  @deprecated("Use ScopedServer instead of ManagedServer", "0.6.0")
  val ManagedServer = scalapb.zio_grpc.ScopedServer
}
