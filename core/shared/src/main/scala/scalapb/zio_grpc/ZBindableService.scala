package scalapb.zio_grpc

import io.grpc.ServerServiceDefinition
import zio.URIO
import zio.Has
import zio.NeedsEnv
import zio.Tag

/** Provides a way to bind a ZIO gRPC service implementations to a server. */
trait ZBindableService[-R, -S] {

  /** Effectfully returns a [[io.grpc.ServerServiceDefinition]] for the given service instance */
  def bindService(serviceImpl: S): URIO[R, ServerServiceDefinition]
}

object ZBindableService {
  def apply[R, S](implicit ev: ZBindableService[R, S]) = ev

  def serviceDefinition[R, S](
      serviceImpl: S
  )(implicit bs: ZBindableService[R, S]): URIO[R, ServerServiceDefinition] =
    bs.bindService(serviceImpl)

  implicit def noEnv[C, S[_, _]: GenericBindable](implicit
      cb: CanBind[C]
  ): ZBindableService[Any, ZGeneratedService[Any, C, S]] =
    new ZBindableService[Any, ZGeneratedService[Any, C, S]] {
      def bindService(serviceImpl: ZGeneratedService[Any, C, S]): zio.URIO[Any, ServerServiceDefinition] =
        serviceImpl.genericBind((c: Has[RequestContext]) => cb.bind(c.get))
    }

  implicit def withEnvAndHasContext[R <: Has[_], C <: Has[_]: Tag, S[_, _]: GenericBindable](implicit
      cb: CanBind[C]
  ): ZBindableService[R, ZGeneratedService[R, C, S]] =
    new ZBindableService[R, ZGeneratedService[R, C, S]] {
      def bindService(serviceImpl: ZGeneratedService[R, C, S]): zio.URIO[R, ServerServiceDefinition] =
        URIO.accessM(r => serviceImpl.genericBind((c: Has[RequestContext]) => r.union[C](cb.bind(c.get))))
    }

  implicit def withRAndNoContext[R: NeedsEnv, S[_, _]: GenericBindable](implicit
      cb: CanBind[Any]
  ): ZBindableService[R, ZGeneratedService[R, Any, S]] =
    new ZBindableService[R, ZGeneratedService[R, Any, S]] {
      def bindService(serviceImpl: ZGeneratedService[R, Any, S]): zio.URIO[R, ServerServiceDefinition] =
        URIO.accessM(r => serviceImpl.genericBind(_ => r))
    }
}
