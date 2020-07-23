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
trait ZTransform[RIn, E, ROut] {
  def effect[A](io: ZIO[RIn, E, A]): ZIO[ROut, E, A]
  def stream[A](io: ZStream[RIn, E, A]): ZStream[ROut, E, A]
}

object ZTransform {

  /** Returns a ZTransform that can provide some of the environment of a service */
  def provideSome[RIn, E, ROut](f: ROut => RIn): ZTransform[RIn, E, ROut] =
    new ZTransform[RIn, E, ROut] {
      def effect[A](io: ZIO[RIn, E, A]): ZIO[ROut, E, A]         = io.provideSome(f)
      def stream[A](io: ZStream[RIn, E, A]): ZStream[ROut, E, A] = io.provideSome(f)
    }

  /** Provides the entire environment of a service (leaving only the context) */
  def provideEnv[R, E, Context](
      env: R
  )(implicit combinable: Combinable[R, Context]): ZTransform[R with Context, E, Context] =
    provideSome(combinable.union(env, _))

  /** Changes the Context type of the service from Context1 to Context2, by
    * applying an effectful function on the environment
    */
  def transformContext[RIn, E, ContextIn <: Has[_]: Tag, ROut <: RIn, ContextOut](
      f: ContextOut => ZIO[ROut, E, ContextIn]
  )(implicit ev: RIn with ContextOut <:< RIn with Has[_]): ZTransform[RIn with ContextIn, E, ROut with ContextOut] =
    new ZTransform[RIn with ContextIn, E, ROut with ContextOut] {
      def effect[A](io: ZIO[RIn with ContextIn, E, A]): ZIO[ROut with ContextOut, E, A] =
        ZIO
          .accessM(f)
          .flatMap(nc => io.provideSome(ev(_).union[ContextIn](nc)))

      def stream[A](io: ZStream[RIn with ContextIn, E, A]): ZStream[ROut with ContextOut, E, A] =
        ZStream
          .fromEffect(ZIO.accessM(f))
          .flatMap(nc => io.provideSome(ev(_).union[ContextIn](nc)))
    }
}
