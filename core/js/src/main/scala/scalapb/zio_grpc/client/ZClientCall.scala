package scalapb.zio_grpc.client

import zio.ZIO
import io.grpc.Status
import scalapb.zio_grpc.SafeMetadata

trait ZClientCall[-R, Req, Res] extends Any {
  def provide(r: R): ZClientCall[Any, Req, Res] = ???
}

object ZClientCall {
  def headersTransformer[R, Req, Res](
      clientCall: ZClientCall[R, Req, Res],
      fetchHeaders: SafeMetadata => ZIO[R, Status, SafeMetadata]
  ) = ???
}
