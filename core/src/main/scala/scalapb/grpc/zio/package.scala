package scalapb.grpc

import io.grpc.Status
import _root_.zio.{IO, ZIO}
import _root_.zio.stream.{Stream, ZStream}

package object zio {
  type GIO[A] = IO[Status, A]

  type GRIO[R, A] = ZIO[R, Status, A]

  type GStream[A] = Stream[Status, A]

  type GRStream[R, A] = ZStream[R, Status, A]
}
