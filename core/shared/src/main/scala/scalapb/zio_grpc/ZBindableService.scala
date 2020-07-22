package scalapb.zio_grpc

import io.grpc.ServerServiceDefinition
import zio.URIO
import zio.ZIO
import zio.Has
import zio.Tag

/** Provides a way to bind a ZIO gRPC service implementations to a server. */
trait ZBindableService[-R, S] {

  /** Effectfully returns a [[io.grpc.ServerServiceDefinition]] for the given service instance */
  def bindService(serviceImpl: S): URIO[R, ServerServiceDefinition]
}
sealed trait Combinable[R, C] {
  def union(r: R, c: C): R with C
}

object Combinable {
  implicit def anyAny: Combinable[Any, Any]                            =
    new Combinable[Any, Any] {
      def union(r: Any, c: Any): Any = r
    }
  implicit def anyHas[C <: Has[_]]: Combinable[Any, C]                 =
    new Combinable[Any, C] {
      def union(r: Any, c: C): C = c
    }
  implicit def hasAny[R <: Has[_]]: Combinable[R, Any]                 =
    new Combinable[R, Any] {
      def union(r: R, c: Any): R = r
    }
  implicit def hasHas[R <: Has[_], C <: Has[_]: Tag]: Combinable[R, C] =
    new Combinable[R, C] {
      def union(r: R, c: C): R with C = r.union[C](c)
    }
}

object ZBindableService {

  def apply[R, S](implicit ev: ZBindableService[R, S]) = ev

  def serviceDefinition[R, S](
      serviceImpl: S
  )(implicit bs: ZBindableService[R, S]): URIO[R, ServerServiceDefinition] =
    bs.bindService(serviceImpl)

  implicit def fromZGeneratedService[R, C, S[-_, -_], T](implicit
      ev1: T <:< ZGeneratedService[R, C, S],
      ev2: CanBind[C],
      ev3: GenericBindable[S],
      ev4: Combinable[R, C]
  ): ZBindableService[R, T] =
    new ZBindableService[R, T] {
      def bindService(s: T): zio.URIO[R, ServerServiceDefinition] =
        ZIO.accessM[R](r => s.genericBind(t => ev4.union(r, ev2.bind(t.get))))
    }
}
