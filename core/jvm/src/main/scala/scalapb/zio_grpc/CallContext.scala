package scalapb.zio_grpc
import io.grpc.Context
import zio.UIO

final class CallContext private (context: Context) extends SafeContext {
  private[zio_grpc] def get[T](key: Context.Key[T]): UIO[Option[T]] = UIO.effectTotal(Option(key.get(context)))
}

object CallContext {
  case class Key[T](name: String, default: Option[T] = Option.empty[T]) extends SafeContext.Key[T] {
    private lazy val jKey = Context.key[T](name)

    override def get(context: SafeContext): UIO[Option[T]] =
      context match {
        case ctx: CallContext => ctx.get(jKey).map(_.orElse(default))
        case _                => UIO.none
      }
  }

  def make(context: Context): SafeContext = new CallContext(context)
}
