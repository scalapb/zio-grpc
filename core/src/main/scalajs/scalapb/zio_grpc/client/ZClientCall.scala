package scalapb.zio_grpc.client

import zio.ZIO
import io.grpc.Status
import scalapb.zio_grpc.SafeMetadata
import zio.ZEnvironment

trait ZClientCall[-R, Req, Res] extends Any {
  def provideEnvironment(r: => ZEnvironment[R]): ZClientCall[Any, Req, Res] = ???
}

object ZClientCall {
  def apply[R, Req, Res](s: String): ZClientCall[R, Req, Res] = ???

  def headersTransformer[R, Req, Res](
      clientCall: ZClientCall[R, Req, Res],
      fetchHeaders: SafeMetadata => ZIO[R, Status, SafeMetadata]
  ) = ???
}
