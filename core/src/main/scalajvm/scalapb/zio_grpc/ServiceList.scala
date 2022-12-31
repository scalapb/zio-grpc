package scalapb.zio_grpc

import zio.{Scope, Tag, ZEnvironment, ZIO, ZLayer}
import io.grpc.ServerServiceDefinition

/** Represents a managed list of services to be added to the a server.
  *
  * This is just a wrapper around a list of ServerServiceDefinition.
  */
sealed class ServiceList[-RR] private[scalapb] (
    val bindAll: ZIO[RR, Throwable, List[ServerServiceDefinition]]
) {

  /** Adds a service to the service list */
  def add[S1: ZBindableService](s1: S1): ServiceList[RR] =
    addZIO[Any, S1](ZIO.succeed(s1))

  /** Adds an effect that returns a service to the service list */
  def addZIO[R1, S1: ZBindableService](
      s1: ZIO[R1, Throwable, S1]
  ): ServiceList[RR with R1] =
    new ServiceList(for {
      ll      <- bindAll
      service <- s1
      ssd     <- ZBindableService[S1].bindService(service)
    } yield ssd :: ll)

  def addLayer[R1, S1: Tag: ZBindableService](layer: ZLayer[R1, Throwable, S1]): ServiceList[RR with R1 with Scope] =
    addZIO[R1 with Scope, S1](layer.build.map(_.get))

  /** Adds a dependency on a service that will be provided later from the environment or a Layer * */
  def access[B: Tag: ZBindableService]: ServiceList[RR with B] =
    addZIO(ZIO.environmentWith(_.get[B]))

  def provideEnvironment(r: => ZEnvironment[RR]): ServiceList[Any] =
    new ServiceList[Any](bindAll.provideEnvironment(r))

  /** Provides layers to this ServiceList which translates it to another layer */
  def provideLayer[R1](layer: ZLayer[R1, Throwable, RR]): ServiceList[R1] =
    new ServiceList(bindAll.provideLayer(layer))

  /** Provides all layers needed by this ServiceList */
  def provide(layer: ZLayer[Any, Throwable, RR]): ServiceList[Any] =
    provideLayer(layer)
}

object ServiceList extends ServiceList(ZIO.succeed(Nil)) {
  val empty = this
}
