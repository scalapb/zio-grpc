package scalapb.zio_grpc

import zio.ZIO
import zio.stream.ZStream
import zio.Has
import zio.Tag

/** Describes a transformation of an a effect or a stream.
  *
  * Instances of this class can be used to provide pre- or post-processing
  * to all methods of a service.
  */
trait ZTransform[R, E, R0] {
  def effect[A](io: ZIO[R, E, A]): ZIO[R0, E, A]
  def stream[A](io: ZStream[R, E, A]): ZStream[R0, E, A]
}

object ZTransform {

  /** Returns a ZTransform that can provide some of the environment of a service */
  def provideSome[R, E, R0](f: R0 => R): ZTransform[R, E, R0] =
    new ZTransform[R, E, R0] {
      def effect[A](io: ZIO[R, E, A]): ZIO[R0, E, A] = io.provideSome(f)
      def stream[A](io: ZStream[R, E, A]): ZStream[R0, E, A] = io.provideSome(f)
    }

  /** Provides the entire environment of a service (leaving only the context) */
  def provideEnv[R <: Has[_], E, Context <: Has[_]: Tag](
      env: R
  ): ZTransform[R with Context, E, Context] =
    provideSome(ctx => env.union(ctx).asInstanceOf[R with Context])

  /** Changes the Context type of the service from Context1 to Context2, by
    * applying an effectful function on the environment */
  def transformContext[R <: Has[_], E, Context1 <: Has[
    _
  ]: Tag, Context2 <: Has[
    _
  ]: Tag](
      f: Context2 => ZIO[R, E, Context1]
  ): ZTransform[R with Context1, E, R with Context2] =
    new ZTransform[R with Context1, E, R with Context2] {
      def effect[A](
          io: ZIO[R with Context1, E, A]
      ): ZIO[R with Context2, E, A] =
        ZIO
          .accessM(f)
          .flatMap(nc =>
            io.provideSome(r0 => r0.union(nc).asInstanceOf[R with Context1])
          )

      def stream[A](
          io: ZStream[R with Context1, E, A]
      ): ZStream[R with Context2, E, A] =
        ZStream
          .fromEffect(ZIO.accessM(f))
          .flatMap(nc =>
            io.provideSome(r0 => r0.union(nc).asInstanceOf[R with Context1])
          )
    }
}
