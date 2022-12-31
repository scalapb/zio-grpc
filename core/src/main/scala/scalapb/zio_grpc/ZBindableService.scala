package scalapb.zio_grpc

import io.grpc.ServerServiceDefinition
import zio.UIO
import zio.Tag
import scala.annotation.implicitNotFound

/** Provides a way to bind a ZIO gRPC service implementations to a server. */
@implicitNotFound("""Could not find an implicit ZBindableService[${S}].

Typically, ${S} should extend ZGeneratedService[C] for some type C which
represents the context provided for each request.

When a ZBindableService could not be found, it is most likely that the context type
 is not Any, SafeMetadata, or RequestContext, or some other type T which has an implicit
  instance of CanBind[T] available.
""")
trait ZBindableService[S] {

  /** Effectfully returns a [[io.grpc.ServerServiceDefinition]] for the given service instance */
  def bindService(serviceImpl: S): UIO[ServerServiceDefinition]
}

object ZBindableService {

  def apply[S](implicit ev: ZBindableService[S]) = ev

  def serviceDefinition[S](
      serviceImpl: S
  )(implicit bs: ZBindableService[S]): UIO[ServerServiceDefinition] =
    bs.bindService(serviceImpl)

  implicit def fromZGeneratedService1[C, S[-_], T](implicit
      ev1: T <:< ZGeneratedService[C, S],
      ev2: T <:< S[C],
      ev3: GenericBindable[S],
      ev4: CanBind[C],
      ev5: TransformableService[S],
      ev6: Tag[C]
  ): ZBindableService[T] =
    new ZBindableService[T] {
      def bindService(s: T): zio.UIO[ServerServiceDefinition] =
        ev3.bind(ev5.transformContext(s, ev4.bind(_)))
    }
}
