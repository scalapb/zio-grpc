package scalapb.zio_grpc

import io.grpc.ServerServiceDefinition
import zio.URIO
import zio.Tag
import _root_.scala.annotation.implicitNotFound

/** Provides a way to bind a ZIO gRPC service implementations to a server. */
@implicitNotFound("""Could not find an implicit ZBindableService[${R}, ${S}].

Typically, ${S} should extend ZGeneratedService[R, C] for some types R and C where R represents
the dependencies of the service, and C represents the context provided for each request.

When a ZBindableService could not be found, it means that either:

1. The provided environment ($R) is missing a dependency of ${S}.

2. The context type is not Any, SafeMetadata, or RequestContext, or some other type T which
has an implicit instance of CanBind[T] available.
""")
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

  implicit def fromZGeneratedService1[R, C, S[-_, -_], T](implicit
      ev1: T <:< ZGeneratedService[R, C, S],
      ev2: T <:< S[R, C],
      ev3: GenericBindable[S],
      ev4: CanBind[C],
      ev5: TransformableService[S],
      ev6: Tag[C]
  ): ZBindableService[R, T] =
    new ZBindableService[R, T] {
      def bindService(s: T): zio.URIO[R, ServerServiceDefinition] =
        ev3.bind(ev5.transformContext(s, ev4.bind(_)))
    }
}
