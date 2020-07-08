package scalapb.zio_grpc

import io.grpc.Status

import zio.{Has, Tag, TagKK, ZIO}
import zio.ZLayer

trait TransformableService[S[_, _]] {
  def transform[R1, C1, R2, C2](
      instance: S[R1, C1],
      transform: ZTransform[R1 with C1, Status, R2 with C2]
  ): S[R2, C2]

  def provide[R](s: S[R, Any], r: R): S[Any, Any] =
    transform(s, ZTransform.provideEnv(r))

  def provide[R <: Has[_], Context <: Has[_]: Tag](s: S[R, Context], r: R): S[Any, Context] =
    transform(s, ZTransform.provideEnv(r))

  def transformContextM[R, C1: Tag, C2: Tag](s: S[R, Has[C1]], f: C2 => ZIO[R, Status, C1]): S[R, Has[C2]] =
    transform[R, Has[C1], R, Has[C2]](
      s,
      ZTransform.transformContext[R, Status, Has[C1], Has[C2]](hc2 => f(hc2.get).map(Has(_)))
    )

  def transformContext[R, C1: Tag, C2: Tag](s: S[R, Has[C1]], f: C2 => C1): S[R, Has[C2]] =
    transformContextM[R, C1, C2](s, (hc2: C2) => ZIO.succeed(f(hc2)))

  def toLayer[R <: Has[_], C <: Has[_]: Tag](s: S[R, C])(implicit ev: TagKK[S]): ZLayer[R, Nothing, Has[S[Any, C]]] =
    ZLayer.fromFunction(provide(s, _))
}

object TransformableService {
  def apply[S[_, _]](implicit ts: TransformableService[S]) = ts

  final class TransformableServiceOps[S[_, _], R, C](private val service: S[R, C]) extends AnyVal {
    def transformContextM[C1: Tag, C2: Tag](
        f: C2 => ZIO[R, Status, C1]
    )(implicit ev: S[R, C] <:< S[R, Has[C1]], TS: TransformableService[S]): S[R, Has[C2]] =
      TS.transformContextM(service, f)

    def provide[R0 <: Has[_], C0 <: Has[_]: Tag](
        r: R0
    )(implicit ev: S[R, C] <:< S[R0, C0], TS: TransformableService[S]): S[Any, C0] =
      TS.provide[R0, C0](service, r)

    def toLayer[R0 <: Has[_], C0 <: Has[_]](implicit
        ev1: S[R, C] <:< S[R0, C0],
        ev2: Tag[C0],
        ev3: TagKK[S],
        TS: TransformableService[S]
    ): ZLayer[R0, Nothing, Has[S[Any, C0]]] = TS.toLayer[R0, C0](service)
  }
}
