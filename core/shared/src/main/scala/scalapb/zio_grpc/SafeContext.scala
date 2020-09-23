package scalapb.zio_grpc

import zio.UIO

private[zio_grpc] trait SafeContext

object SafeContext {
  case object Root extends SafeContext

  trait Key[T] {
    protected def name: String
    protected def default: Option[T]

    def get(context: SafeContext): UIO[Option[T]]
  }
}
