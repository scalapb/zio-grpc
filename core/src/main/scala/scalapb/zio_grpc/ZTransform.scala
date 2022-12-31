package scalapb.zio_grpc

import zio.{IO, Tag, ZEnvironment, ZIO}
import zio.stream.ZStream

/** Describes a transformation of an effect or a stream.
  *
  * Instances of this class can be used to apply a transformation to all methods of a service to generate a new
  * "decorated" service. This can be used for pre- or post-processing of requests/responses and also for environment and
  * context transformations.
  */
trait ZTransform[+ContextIn, E, -ContextOut] { self =>
  def effect[A](io: ZIO[ContextIn, E, A]): ZIO[ContextOut, E, A]
  def stream[A](io: ZStream[ContextIn, E, A]): ZStream[ContextOut, E, A]

  /** Combine two ZTransforms
    */
  def andThen[ContextIn2 <: ContextOut, ContextOut2](
      zt: ZTransform[ContextIn2, E, ContextOut2]
  ): ZTransform[ContextIn, E, ContextOut2] =
    new ZTransform[ContextIn, E, ContextOut2] {
      override def effect[A](io: ZIO[ContextIn, E, A]): ZIO[ContextOut2, E, A] =
        zt.effect(self.effect(io))

      override def stream[A](io: ZStream[ContextIn, E, A]): ZStream[ContextOut2, E, A] =
        zt.stream(self.stream(io))
    }
}

object ZTransform {

  /** Changes the Context type of the service from Context1 to Context2, by applying an effectful function on the
    * environment before the request is further processed.
    */
  def transformContext[ContextIn: Tag, E, ContextOut: Tag](
      f: ContextOut => IO[E, ContextIn]
  ): ZTransform[ContextIn, E, ContextOut] =
    new ZTransform[ContextIn, E, ContextOut] {
      def effect[A](io: ZIO[ContextIn, E, A]): ZIO[ContextOut, E, A] =
        ZIO
          .environmentWithZIO { (env: ZEnvironment[ContextOut]) =>
            f(env.get[ContextOut])
          }
          .flatMap { env =>
            io.provideEnvironment(ZEnvironment(env))
          }

      def stream[A](io: ZStream[ContextIn, E, A]): ZStream[ContextOut, E, A] =
        ZStream
          .fromZIO(ZIO.environmentWithZIO { (env: ZEnvironment[ContextOut]) =>
            f(env.get[ContextOut])
          })
          .flatMap { env =>
            io.provideEnvironment(ZEnvironment(env))
          }
    }
}
