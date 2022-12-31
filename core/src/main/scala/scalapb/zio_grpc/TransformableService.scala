package scalapb.zio_grpc

import io.grpc.Status

import zio.{IO, Tag, ZIO, ZLayer}
import zio.ULayer

trait TransformableService[S[_]] {
  def transform[ContextIn, ContextOut](
      instance: S[ContextIn],
      transform: ZTransform[ContextIn, Status, ContextOut]
  ): S[ContextOut]

  def transformContextZIO[FromContext: Tag, ToContext: Tag](
      s: S[FromContext],
      f: ToContext => IO[Status, FromContext]
  ): S[ToContext] =
    transform[FromContext, ToContext](s, ZTransform.transformContext[FromContext, Status, ToContext](f))

  def transformContext[FromContext: Tag, ToContext: Tag](
      s: S[FromContext],
      f: ToContext => FromContext
  ): S[ToContext] =
    transformContextZIO[FromContext, ToContext](s, (hc2: ToContext) => ZIO.succeed(f(hc2)))
}

object TransformableService {
  def apply[S[_]](implicit ts: TransformableService[S]) = ts

  final class TransformableServiceOps[S[_], C](private val service: S[C]) extends AnyVal {
    def transform[ContextOut](
        transform: ZTransform[C, Status, ContextOut]
    )(implicit TS: TransformableService[S]): S[ContextOut] =
      TS.transform[C, ContextOut](service, transform)

    def transformContextZIO[C2: Tag](
        f: C2 => IO[Status, C]
    )(implicit TS: TransformableService[S], cTagged: Tag[C]): S[C2] =
      TS.transformContextZIO[C, C2](service, f)

    def toLayer(implicit tag: Tag[S[C]]): ULayer[S[C]] = ZLayer.succeed(service)
  }
}
