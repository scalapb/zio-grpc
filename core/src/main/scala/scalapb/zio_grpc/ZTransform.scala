package scalapb.zio_grpc

import zio.ZIO
import zio.stream.ZStream

/** Describes a transformation of an effect or a stream.
  *
  * Instances of this class can be used to apply a transformation to all methods of a service to generate a new
  * "decorated" service. This can be used for pre- or post-processing of requests/response or to transform the context.
  */
trait ZTransform[ContextIn, E, ContextOut] { self =>
  def effect[A](io: ContextIn => ZIO[Any, E, A])(context: ContextOut): ZIO[Any, E, A]
  def stream[A](io: ContextIn => ZStream[Any, E, A])(context: ContextOut): ZStream[Any, E, A]

  /** Combine two ZTransforms
    */
  /*
  def andThen[Context2 <: Context](
      zt: ZTransform[Context2]
  ): ZTransform[Context2] =
    new ZTransform[Context2] {
      override def effect[A](context: Context2, io: ZIO[Any, Status, A]): ZIO[Any, Status, A] =
        zt.effect(context, self.effect(context, io))

      override def stream[A](context: Context2, io: ZStream[Any, Status, A]): ZStream[Any, Status, A] =
        zt.stream(context, self.stream(context, io))
    }
   */

  /*
  def transformContext[Context2](f: Context2 => IO[Status, Context]): ZTransform[Context2] = new ZTransform[Context2] {
    def effect[A](context: Context2, io: ZIO[Any,Status,A]): ZIO[Any,Status,A] = f(context).flatMap(ctx => self.effect(ctx, io))

    def stream[A](context: Context2, io: ZStream[Any,Status,A]): ZStream[Any,Status,A] = ZStream.fromZIO(f(context)).flatMap(ctx => self.stream(ctx, io))
  }
   */
}

object ZTransform {
  def transformContext[ContextIn, E, ContextOut](f: ContextOut => ZIO[Any, E, ContextIn]) =
    new ZTransform[ContextIn, E, ContextOut] {
      def effect[A](io: ContextIn => ZIO[Any, E, A])(context: ContextOut): ZIO[Any, E, A] = f(context).flatMap(io)

      def stream[A](io: ContextIn => ZStream[Any, E, A])(context: ContextOut): ZStream[Any, E, A] =
        ZStream.fromZIO(f(context)).flatMap(io)
    }
}
