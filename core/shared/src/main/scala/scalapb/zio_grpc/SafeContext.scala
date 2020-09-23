package scalapb.zio_grpc

import zio._

private[zio_grpc] trait SafeContext

object SafeContext {
  case object Root extends SafeContext

  trait Key[R, E, A] {
    protected def name: String
    protected def default: ZIO[R, E, A]

    def get: ZIO[R with Has[RequestContext], E, A]
  }
}
