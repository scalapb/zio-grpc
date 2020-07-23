package scalapb.zio_grpc

import io.grpc.Status

import zio.{Has, Tag, TagKK, ZIO}
import zio.ZLayer

trait TransformableService[S[_, _]] {
  def transform[R1, C1, R2, C2](
      instance: S[R1, C1],
      transform: ZTransform[R1 with C1, Status, R2 with C2]
  ): S[R2, C2]

  def provide[R, Context](s: S[R, Context], r: R)(implicit ev: Combinable[R, Context]): S[Any, Context] =
    transform(s, ZTransform.provideEnv(r))

  def transformContextM[R, C: Tag, R2 <: R, C2: Tag](s: S[R, Has[C]], f: C2 => ZIO[R2, Status, C]): S[R2, Has[C2]] =
    transform[R, Has[C], R2, Has[C2]](
      s,
      ZTransform.transformContext[R, Status, Has[C], R2, Has[C2]](hc2 => f(hc2.get).map(Has(_)))
    )

  def transformContext[R, C1: Tag, C2: Tag](s: S[R, Has[C1]], f: C2 => C1): S[R, Has[C2]] =
    transformContextM[R, C1, R, C2](s, (hc2: C2) => ZIO.succeed(f(hc2)))

  def toLayer[R, C: Tag](
      s: S[R, C]
  )(implicit ev1: TagKK[S], ev2: Combinable[R, C]): ZLayer[R, Nothing, Has[S[Any, C]]] =
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
    )(implicit TS: TransformableService[S], ev1: Combinable[R, C], ev2: S[R, C] <:< S[R0, C0]): S[Any, C] =
      TS.provide[R, C](service, r)

    def toLayer[R0, C0](implicit
        ev1: S[R, C] <:< S[R0, C0],
        ev2: Combinable[R0, C0],
        ev3: Tag[C0],
        ev4: TagKK[S],
        TS: TransformableService[S]
    ): ZLayer[R0, Nothing, Has[S[Any, C0]]] = TS.toLayer[R0, C0](service)
  }
}
