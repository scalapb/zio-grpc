package scalapb.zio_grpc

import io.grpc.Metadata

final case class ResponseContext[A](
    headers: Metadata,
    response: A,
    trailers: Metadata
)
