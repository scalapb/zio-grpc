package scalapb.zio_grpc

import zio.{Has, Tag}
import io.grpc.ServerServiceDefinition
import zio.ZManaged
import zio.ZIO

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
    addManaged[R1, R2, S1](s1.toManaged_)

  def addManaged[R1 <: RR, R2 <: RR, S1](s1: ZManaged[R1 with R2, Throwable, S1])(implicit
      bs: ZBindableService[R1, S1]
  ): ServiceList[RR with R1 with R2] =
    new ServiceList(for {
      l  <- bindAll
      sd <- s1.mapM(bs.bindService(_))
    } yield sd :: l)

  /** Adds a dependency on a service that will be provided later from the environment or a Layer * */
  def access[B: Tag](implicit bs: ZBindableService[Any, B]): ServiceList[Has[B] with RR] =
    accessEnv[Any, B]

  def accessEnv[R, B: Tag](implicit bs: ZBindableService[R, B]): ServiceList[R with Has[B] with RR] =
    new ServiceList(ZManaged.accessManaged[R with Has[B] with RR] { r =>
      bindAll.mapM(ll => bs.bindService(r.get[B]).map(_ :: ll))
    })

  def provide(r: RR): ServiceList[Any] = new ServiceList[Any](bindAll.provide(r))
}

object ServiceList extends ServiceList(ZManaged.succeed(Nil)) {
  val empty = this
}
