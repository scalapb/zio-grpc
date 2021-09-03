package scalapb.zio_grpc

import io.grpc.ServerServiceDefinition
import zio.URIO
import zio.ZIO

/** Provides a way to bind a ZIO gRPC service implementations to a server. */
trait ZBindableService[-R, S] {

  /** Effectfully returns a [[io.grpc.ServerServiceDefinition]] for the given service instance */
  def bindService(serviceImpl: S): URIO[R, ServerServiceDefinition]
}
object ZBindableService {

  def apply[R, S](implicit ev: ZBindableService[R, S]) = ev

  def serviceDefinition[R, S](
      serviceImpl: S
  )(implicit bs: ZBindableService[R, S]): URIO[R, ServerServiceDefinition] =
    bs.bindService(serviceImpl)

  implicit def fromZGeneratedService1[R <: zio.Has[_], C, S[-_, -_], T](implicit
      ev1: T <:< ZGeneratedService[R, C, S],
      ev2: T <:< S[R, C],
      ev3: GenericBindable[S],
      ev4: CanBind[C],
      ev5: zio.Has.Union[R, C]
  ): ZBindableService[R, T] =
    new ZBindableService[R, T] {
      def bindService(s: T): zio.URIO[R, ServerServiceDefinition] =
        ZIO.accessZIO[R](r => ev3.bind(s, t => ev5.union(r, ev4.bind(t))))
    }

  implicit def fromZGeneratedService2[C, S[-_, -_], T](implicit
      ev1: T <:< ZGeneratedService[Any, C, S],
      ev2: T <:< S[Any, C],
      ev3: GenericBindable[S],
      ev4: CanBind[C]
  ): ZBindableService[Any, T] =
    new ZBindableService[Any, T] {
      def bindService(s: T): zio.URIO[Any, ServerServiceDefinition] =
        ZIO.accessZIO[Any](_ => ev3.bind(s, t => ev4.bind(t)))
    }
}
