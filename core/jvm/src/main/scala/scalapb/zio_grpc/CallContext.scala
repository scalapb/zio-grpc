package scalapb.zio_grpc
import io.grpc.Context
import zio.{UIO, ZIO}

final private class CallContext private (context: Context) extends SafeContext {
  private[zio_grpc] def get[T](key: Context.Key[T]): Option[T] = Option(key.get(context))
}

object CallContext {

  private case class Key[R, E, A](name: String, default: ZIO[R, E, A]) extends SafeContext.Key[R, E, A] {
    private lazy val jKey = Context.key[A](name)

    override def get(context: SafeContext): ZIO[R, E, A] =
      context match {
        case ctx: CallContext => ZIO.fromOption(ctx.get(jKey)).catchAll(_ => default)
        case _                => default
      }
  }

  def make(context: Context): SafeContext = new CallContext(context)

  def key[R, E, A](name: String, default: ZIO[R, E, A]): SafeContext.Key[R, E, A] = Key(name, default)
}
