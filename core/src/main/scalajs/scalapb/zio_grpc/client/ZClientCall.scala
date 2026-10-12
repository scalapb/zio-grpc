package scalapb.zio_grpc.client

import zio.IO
import io.grpc.StatusException
import scalapb.zio_grpc.SafeMetadata

trait ZClientCall[Req, Res] extends Any {
  def isReady: zio.UIO[Boolean] = zio.ZIO.succeed(true)
}

object ZClientCall {
  def apply[Req, Res](s: String): ZClientCall[Req, Res] = ???

  def headersTransformer[Req, Res](
      clientCall: ZClientCall[Req, Res],
      fetchHeaders: SafeMetadata => IO[StatusException, SafeMetadata]
  ) = ???
}
