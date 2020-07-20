package scalapb.zio_grpc

import zio.ZIO
import zio.stream.ZStream
import zio.Has
import zio.Tag

/** Describes a transformation of an a effect or a stream.
  *
  * Instances of this class can be used to apply a transformation to all methods of a service
  * to generate a new "decorated" service. This can be used for pre- or post-processing of
  * requests/responses and also for environment and context transformations.
  */
trait ZTransform[R, E, R0] {
  def effect[A](io: ZIO[R, E, A]): ZIO[R0, E, A]
  def stream[A](io: ZStream[R, E, A]): ZStream[R0, E, A]
}

object ZTransform {

  /** Returns a ZTransform that can provide some of the environment of a service */
  def provideSome[R, E, R0](f: R0 => R): ZTransform[R, E, R0] =
    new ZTransform[R, E, R0] {
      def effect[A](io: ZIO[R, E, A]): ZIO[R0, E, A]         = io.provideSome(f)
      def stream[A](io: ZStream[R, E, A]): ZStream[R0, E, A] = io.provideSome(f)
    }

  /** Provides the entire environment of a service (leaving only the context) */
  def provideEnv[R <: Has[_], E, Context <: Has[_]: Tag](
      env: R
  ): ZTransform[R with Context, E, Context] =
    provideSome(env.union[Context])

  def provideEnv[R, E](env: R): ZTransform[R, E, Any] = provideSome((_: Any) => env)

  /** Changes the Context type of the service from Context1 to Context2, by
    * applying an effectful function on the environment
    */
  def transformContext[R, E, Context1 <: Has[_]: Tag, R2 <: R, Context2](
      f: Context2 => ZIO[R2, E, Context1]
  )(implicit ev: R with Context2 <:< R with Has[_]): ZTransform[R with Context1, E, R2 with Context2] =
    new ZTransform[R with Context1, E, R2 with Context2] {
      def effect[A](io: ZIO[R with Context1, E, A]): ZIO[R2 with Context2, E, A] =
        ZIO
          .accessM(f)
          .flatMap(nc => io.provideSome(ev(_).union[Context1](nc)))

      def stream[A](io: ZStream[R with Context1, E, A]): ZStream[R2 with Context2, E, A] =
        ZStream
          .fromEffect(ZIO.accessM(f))
          .flatMap(nc => io.provideSome(ev(_).union[Context1](nc)))
    }
}
