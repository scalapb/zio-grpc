package scalapb

import io.grpc.Status
import zio.IO
import zio.stream.Stream
import zio.Managed
import zio.Has

package object zio_grpc {
  type GIO[+A] = IO[Status, A]

  type GStream[A] = Stream[Status, A]

  type Server = Has[Server.Service]

  type ZManagedChannel[R] = Managed[Throwable, ZChannel[R]]
}
