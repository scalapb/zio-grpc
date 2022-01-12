package scalapb.zio_grpc

import zio.{Tag, ZIO, ZLayer, ZManaged}
import io.grpc.ServerServiceDefinition
import zio.IsNotIntersection
import zio.ZEnvironment

/** Represents a managed list of services to be added to the a server.
  *
  * This is just a wrapper around a list of ServerServiceDefinition.
  */
sealed class ServiceList[-RR] private[scalapb] (val bindAll: ZManaged[RR, Throwable, List[ServerServiceDefinition]]) {

  /** Adds a service to the service list */
  def add[R1 <: RR, S1](s1: S1)(implicit b: ZBindableService[R1, S1]): ServiceList[R1] =
    addManaged[R1, RR, S1](ZManaged.succeed(s1))

  /** Adds an effect that returns a service to the service list */
  def addM[R1 <: RR, R2 <: RR, S1](
      s1: ZIO[R2, Throwable, S1]
  )(implicit b: ZBindableService[R1, S1]): ServiceList[R1 with R2] =
    addManaged[R1, R2, S1](s1.toManaged)

  def addManaged[R1 <: RR, R2 <: RR, S1](s1: ZManaged[R2, Throwable, S1])(implicit
      bs: ZBindableService[R1, S1]
  ): ServiceList[RR with R1 with R2] =
    new ServiceList(for {
      l  <- bindAll
      sd <- s1.mapZIO(bs.bindService(_))
    } yield sd :: l)

  /** Adds a dependency on a service that will be provided later from the environment or a Layer * */
  def access[B: IsNotIntersection: Tag](implicit bs: ZBindableService[Any, B]): ServiceList[B with RR] =
    accessEnv[Any, B]

  def accessEnv[R, B: IsNotIntersection: Tag](implicit bs: ZBindableService[R, B]): ServiceList[R with B with RR] =
    new ServiceList(ZManaged.environmentWithManaged[R with B with RR] { r =>
      bindAll.mapZIO(ll => bs.bindService(r.get[B]).map(_ :: ll))
    })

  def provideEnvironment(r: => ZEnvironment[RR]): ServiceList[Any] = new ServiceList[Any](bindAll.provideEnvironment(r))
}

object ServiceList extends ServiceList(ZManaged.succeed(Nil)) {
  val empty = this
}
