package scalapb.zio_grpc

import io.grpc.Status

import zio.{Has, Tag, TagKK, ZIO}
import zio.ZLayer

trait TransformableService[S[_, _]] {
  def transform[RIn, ContextIn, ROut, ContextOut](
      instance: S[RIn, ContextIn],
      transform: ZTransform[RIn with ContextIn, Status, ROut with ContextOut]
  ): S[ROut, ContextOut]

  def provide[R, Context](s: S[R, Context], r: R)(implicit ev: Has.Union[R, Context]): S[Any, Context] =
    transform(s, ZTransform.provideEnv(r))

  def transformContextM[R, FromContext: Tag, R0 <: R, ToContext: Tag](
      s: S[R, Has[FromContext]],
      f: ToContext => ZIO[R0, Status, FromContext]
  ): S[R0, Has[ToContext]] =
    transform[R, Has[FromContext], R0, Has[ToContext]](
      s,
      ZTransform.transformContext[R, Status, Has[FromContext], R0, Has[ToContext]](hc2 => f(hc2.get).map(Has(_)))
    )

  def transformContext[R, FromContext: Tag, ToContext: Tag](
      s: S[R, Has[FromContext]],
      f: ToContext => FromContext
  ): S[R, Has[ToContext]] =
    transformContextM[R, FromContext, R, ToContext](s, (hc2: ToContext) => ZIO.succeed(f(hc2)))

  def toLayer[R, C: Tag](
      s: S[R, C]
  )(implicit ev1: TagKK[S], ev2: Has.Union[R, C]): ZLayer[R, Nothing, Has[S[Any, C]]] =
    ZLayer.fromFunction(provide(s, _))
}

object TransformableService {
  def apply[S[_, _]](implicit ts: TransformableService[S]) = ts

  final class TransformableServiceOps[S[_, _], R, C](private val service: S[R, C]) extends AnyVal {
    def transformContextM[C1: Tag, C2: Tag, R0 <: R](
        f: C2 => ZIO[R0, Status, C1]
    )(implicit ev: S[R, C] <:< S[R, Has[C1]], TS: TransformableService[S]): S[R0, Has[C2]] =
      TS.transformContextM(service, f)

    def provide[R0, C0](
        r: R
    )(implicit TS: TransformableService[S], ev1: Has.Union[R, C], ev2: S[R, C] <:< S[R0, C0]): S[Any, C] =
      TS.provide[R, C](service, r)

    def toLayer[R0, C0](implicit
        ev1: S[R, C] <:< S[R0, C0],
        ev2: Has.Union[R0, C0],
        ev3: Tag[C0],
        ev4: TagKK[S],
        TS: TransformableService[S]
    ): ZLayer[R0, Nothing, Has[S[Any, C0]]] = TS.toLayer[R0, C0](service)
  }
}
