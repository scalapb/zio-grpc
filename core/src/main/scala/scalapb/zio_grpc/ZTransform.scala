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
}

object ZTransform {
  // Returns a ZTransform that effectfully transforms the context parameter
  def transformContext[ContextIn, E, ContextOut](f: ContextOut => ZIO[Any, E, ContextIn]) =
    new ZTransform[ContextIn, E, ContextOut] {
      def effect[A](io: ContextIn => ZIO[Any, E, A])(context: ContextOut): ZIO[Any, E, A] = f(context).flatMap(io)

      def stream[A](io: ContextIn => ZStream[Any, E, A])(context: ContextOut): ZStream[Any, E, A] =
        ZStream.fromZIO(f(context)).flatMap(io)
    }
}
