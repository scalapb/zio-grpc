package scalapb.zio_grpc

import zio.{Scope, Tag, ZEnvironment, ZIO, ZLayer}
import io.grpc.ServerServiceDefinition

/** Represents a managed list of services to be added to the a server.
  *
  * This is just a wrapper around a list of ServerServiceDefinition.
  */
sealed class ServiceList[-RR] private[scalapb] (
    val bindAll: ZIO[RR with Scope, Throwable, List[ServerServiceDefinition]]
) {

  /** Adds a service to the service list */
  def add[R1 <: RR, S1](s1: S1)(implicit b: ZBindableService[R1, S1]): ServiceList[R1] =
    addScoped[R1, RR, S1](ZIO.succeed(s1))

  /** Adds an effect that returns a service to the service list */
  def addZIO[R1 <: RR, R2 <: RR, S1](
      s1: ZIO[R2, Throwable, S1]
  )(implicit b: ZBindableService[R1, S1]): ServiceList[R1 with R2] =
    addScoped[R1, R2, S1](s1)

  def addScoped[R1 <: RR, R2 <: RR, S1](s1: ZIO[R2 with Scope, Throwable, S1])(implicit
      bs: ZBindableService[R1, S1]
  ): ServiceList[RR with R1 with R2] =
    new ServiceList[RR with R1 with R2](for {
      l  <- bindAll
      sd <- s1.flatMap(bs.bindService(_))
    } yield sd :: l)

  def addLayer[R <: RR, S1: Tag](layer: ZLayer[R, Throwable, S1])(implicit
      bs: ZBindableService[R, S1]
  ): ServiceList[RR with R] =
    addScoped[R, R, S1](layer.build.map(_.get))

  /** Adds a dependency on a service that will be provided later from the environment or a Layer * */
  def access[B: Tag](implicit bs: ZBindableService[Any, B]): ServiceList[B with RR] =
    accessEnv[Any, B]

  def accessEnv[R, B: Tag](implicit bs: ZBindableService[R, B]): ServiceList[R with B with RR] =
    new ServiceList[R with B with RR](ZIO.environmentWithZIO[R with B with RR] { r =>
      bindAll.flatMap(ll => bs.bindService(r.get[B]).map(_ :: ll))
    })

  def provideEnvironment(r: => ZEnvironment[RR]): ServiceList[Any] =
    new ServiceList[Any](bindAll.provideSomeEnvironment[Scope](r.union[Scope](_)))
}

object ServiceList extends ServiceList(ZIO.succeed(Nil)) {
  val empty = this
}
