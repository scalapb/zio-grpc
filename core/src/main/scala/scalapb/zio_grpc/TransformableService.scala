package scalapb.zio_grpc

import io.grpc.Status

import zio.ZLayer
import zio.ZEnvironment
import zio.Tag
import zio.ZIO

trait TransformableService[S[_, _]] {
  def transform[RIn, ContextIn, ROut, ContextOut](
      instance: S[RIn, ContextIn],
      transform: ZTransform[RIn with ContextIn, Status, ROut with ContextOut]
  ): S[ROut, ContextOut]

  def provideEnvironment[R, Context: Tag](s: S[R, Context], r: ZEnvironment[R]): S[Any, Context] =
    transform(s, ZTransform.provideEnvironment[R, Status, Context](r))

  def transformContextM[R, FromContext: Tag, R0 <: R, ToContext: Tag](
      s: S[R, FromContext],
      f: ToContext => ZIO[R0, Status, FromContext]
  ): S[R0, ToContext] =
    transform[R, FromContext, R0, ToContext](s, ZTransform.transformContext[R, Status, FromContext, R0, ToContext](f))

  def transformContext[R, FromContext: Tag, ToContext: Tag](
      s: S[R, FromContext],
      f: ToContext => FromContext
  ): S[R, ToContext] =
    transformContextM[R, FromContext, R, ToContext](s, (hc2: ToContext) => ZIO.succeed(f(hc2)))

  def toLayer[R, C: Tag](
      s: S[R, C]
  )(implicit tagged: Tag[S[Any, C]]): ZLayer[R, Nothing, S[Any, C]] =
    ZLayer(ZIO.environmentWith((r: ZEnvironment[R]) => provideEnvironment(s, r)))
}

object TransformableService {
  def apply[S[_, _]](implicit ts: TransformableService[S]) = ts

  final class TransformableServiceOps[S[_, _], R, C](private val service: S[R, C]) extends AnyVal {
    def transform[ROut, ContextOut](
        transform: ZTransform[R with C, Status, ROut with ContextOut]
    )(implicit TS: TransformableService[S]): S[ROut, ContextOut] =
      TS.transform[R, C, ROut, ContextOut](service, transform)

    def transformContextM[C2: Tag, R0 <: R](
        f: C2 => ZIO[R0, Status, C]
    )(implicit TS: TransformableService[S], cTagged: Tag[C]): S[R0, C2] =
      TS.transformContextM[R, C, R0, C2](service, f)

    def provideEnvironment(
        r: ZEnvironment[R]
    )(implicit TS: TransformableService[S], cTagged: Tag[C]): S[Any, C] =
      TS.provideEnvironment[R, C](service, r)

    def toLayer(implicit
        TS: TransformableService[S],
        sTagged: Tag[S[Any, C]],
        cTagged: Tag[C]
    ): ZLayer[R, Nothing, S[Any, C]] = TS.toLayer[R, C](service)
  }
}
