package scalapb.zio_grpc

import zio.{EnvironmentTag, Tag, ZEnvironment, ZIO}
import zio.stream.ZStream

/** Describes a transformation of an effect or a stream.
  *
  * Instances of this class can be used to apply a transformation to all methods of a service to generate a new
  * "decorated" service. This can be used for pre- or post-processing of requests/responses and also for environment and
  * context transformations.
  */
trait ZTransform[+RIn, E, -ROut] { self =>
  def effect[A](io: ZIO[RIn, E, A]): ZIO[ROut, E, A]
  def stream[A](io: ZStream[RIn, E, A]): ZStream[ROut, E, A]

  /** Combine two ZTransforms
    */
  def andThen[RIn2 <: ROut, ROut2](
      zt: ZTransform[RIn2, E, ROut2]
  ): ZTransform[RIn, E, ROut2] =
    new ZTransform[RIn, E, ROut2] {
      override def effect[A](io: ZIO[RIn, E, A]): ZIO[ROut2, E, A] =
        zt.effect(self.effect(io))

      override def stream[A](io: ZStream[RIn, E, A]): ZStream[ROut2, E, A] =
        zt.stream(self.stream(io))
    }
}

object ZTransform {

  /** Returns a ZTransform that can provide some of the environment of a service */
  def provideSomeEnvironment[RIn, E, ROut](f: ZEnvironment[ROut] => ZEnvironment[RIn]): ZTransform[RIn, E, ROut] =
    new ZTransform[RIn, E, ROut] {
      def effect[A](io: ZIO[RIn, E, A]): ZIO[ROut, E, A]         = io.provideSomeEnvironment(f)
      def stream[A](io: ZStream[RIn, E, A]): ZStream[ROut, E, A] = io.provideSomeEnvironment(f)
    }

  /** Provides the entire environment of a service (leaving only the context) */
  def provideEnvironment[R, E, Context: EnvironmentTag](
      env: => ZEnvironment[R]
  ): ZTransform[R with Context, E, Context] =
    provideSomeEnvironment((ctx: ZEnvironment[Context]) => env.union[Context](ctx))

  /** Changes the Context type of the service from Context1 to Context2, by applying an effectful function on the
    * environment
    */
  def transformContext[RIn, E, ContextIn: Tag, ROut <: RIn, ContextOut: Tag](
      f: ContextOut => ZIO[ROut, E, ContextIn]
  ): ZTransform[RIn with ContextIn, E, ROut with ContextOut] =
    new ZTransform[RIn with ContextIn, E, ROut with ContextOut] {
      def effect[A](io: ZIO[RIn with ContextIn, E, A]): ZIO[ROut with ContextOut, E, A] =
        ZIO
          .environmentWithZIO { (env: ZEnvironment[ROut with ContextOut]) =>
            f(env.get[ContextOut]).map(cin => env.add[ContextIn](cin))
          }
          .flatMap { env =>
            io.provideEnvironment(env)
          }

      def stream[A](io: ZStream[RIn with ContextIn, E, A]): ZStream[ROut with ContextOut, E, A] =
        ZStream
          .fromZIO(ZIO.environmentWithZIO { (env: ZEnvironment[ROut with ContextOut]) =>
            f(env.get[ContextOut]).map(cin => env.add[ContextIn](cin))
          })
          .flatMap { env =>
            io.provideEnvironment(env)
          }
    }
}
