package scalapb.zio_grpc

import io.grpc.Metadata
import io.grpc.Status

sealed abstract class ResponseFrame[+A]

object ResponseFrame {
  final case class Headers(headers: Metadata)                   extends ResponseFrame[Nothing]
  final case class Message[A](message: A)                       extends ResponseFrame[A]
  final case class Trailers(status: Status, trailers: Metadata) extends ResponseFrame[Nothing]
}
