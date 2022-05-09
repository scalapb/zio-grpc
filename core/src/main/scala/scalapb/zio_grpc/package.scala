package scalapb

import io.grpc.Status
import zio.{IO, Scope, ZIO}
import zio.stream.Stream

package object zio_grpc {
  type GIO[A] = IO[Status, A]

  type GStream[A] = Stream[Status, A]

  type Server = Server.Service

  type ZManagedChannel[R] = ZIO[Scope, Throwable, ZChannel[R]]
}
