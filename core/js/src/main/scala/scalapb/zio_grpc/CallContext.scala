package scalapb.zio_grpc
import zio._

case object CallContext {
  case object Root extends SafeContext

  private case class Key[R, E, A](name: String, default: ZIO[R, E, A]) extends SafeContext.Key[R, E, A] {
    override def get: ZIO[R with Has[RequestContext], E, A] = default
  }

  def key[R, E, A](name: String, default: ZIO[R, E, A]): SafeContext.Key[R, E, A] = Key(name, default)
}
