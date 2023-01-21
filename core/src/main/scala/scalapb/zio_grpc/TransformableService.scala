package scalapb.zio_grpc

import io.grpc.Status

import zio.{IO, Tag, ZIO, ZLayer}
import zio.ULayer

trait TransformableService[S[_]] {
  def transform[ContextIn, ContextOut](
      instance: S[ContextIn],
      transform: ZTransform[ContextIn, Status, ContextOut]
  ): S[ContextOut]

  def transformContextZIO[ContextIn, ContextOut](
      instance: S[ContextIn],
      f: ContextOut => IO[Status, ContextIn]
  ): S[ContextOut] = transform(instance, ZTransform.transformContext(f))

  def transformContext[ContextIn, ContextOut](
      instance: S[ContextIn],
      f: ContextOut => ContextIn
  ): S[ContextOut] = transformContextZIO(instance, (c: ContextOut) => ZIO.succeed(f(c)))
}

object TransformableService {
  def apply[S[_]](implicit ts: TransformableService[S]) = ts

  final class TransformableServiceOps[S[_], C](private val service: S[C]) extends AnyVal {
    def transformContextZIO[ContextOut](
        transform: ContextOut => IO[Status, C]
    )(implicit TS: TransformableService[S]): S[ContextOut] =
      TS.transformContextZIO[C, ContextOut](service, transform)

    def toLayer(implicit tag: Tag[S[C]]): ULayer[S[C]] = ZLayer.succeed(service)
  }
}
