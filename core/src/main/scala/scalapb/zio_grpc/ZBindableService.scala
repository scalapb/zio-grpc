package scalapb.zio_grpc

import io.grpc.ServerServiceDefinition
import io.grpc.StatusException
import zio.UIO
import zio.Tag
import scala.annotation.implicitNotFound

/** Provides a way to bind a ZIO gRPC service implementations to a server. */
@implicitNotFound("""Could not find an implicit ZBindableService[${S}].

Typically, ${S} should extend GenericGeneratedService[C] for some type C which
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

  implicit def fromGenericGeneratedService[C, S[-_, +_], T](implicit
      ev1: T <:< GenericGeneratedService[C, StatusException, S],
      ev2: T <:< S[C, StatusException],
      ev3: GenericBindable[S[RequestContext, StatusException]],
      ev4: CanBind[C],
      ev6: Tag[C]
  ): ZBindableService[T] =
    new ZBindableService[T] {
      def bindService(s: T): zio.UIO[ServerServiceDefinition] =
        ev3.bind(s.transformContext[RequestContext](ev4.bind(_)))
    }

  implicit def fromGeneratedService[T <: GeneratedService](implicit
      ev: GenericBindable[T]
  ): ZBindableService[T] =
    new ZBindableService[T] {
      def bindService(serviceImpl: T): UIO[ServerServiceDefinition] = ev.bind(serviceImpl)
    }
}
