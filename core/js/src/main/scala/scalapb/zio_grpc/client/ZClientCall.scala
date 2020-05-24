package scalapb.zio_grpc.client

import zio.ZIO
import io.grpc.Metadata
import io.grpc.Status

trait ZClientCall[-R, Req, Res] extends Any

object ZClientCall {
  def headersTransformer[R, Req, Res](
      clientCall: ZClientCall[R, Req, Res],
      fetchHeaders: Metadata => ZIO[R, Status, Metadata]
  ) = ???
}
