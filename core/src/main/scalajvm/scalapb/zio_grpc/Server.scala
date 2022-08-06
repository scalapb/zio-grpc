package scalapb.zio_grpc

import zio.{Duration, Scope, Tag, Task, URIO, ZIO, ZLayer}
import io.grpc.ServerBuilder
import io.grpc.ServerServiceDefinition
import java.util.concurrent.TimeUnit

object Server {
  trait Service {
    def port: Task[Int]

    def shutdown: Task[Unit]

    def shutdownNow: Task[Unit]

    def start: Task[Unit]
  }

  private[zio_grpc] class ServiceImpl(underlying: io.grpc.Server) extends Service {
    private def awaitTermination(duration: Option[Duration]): Task[Unit] =
      ZIO.attempt(duration match {
        case None           =>
          underlying.awaitTermination()
        case Some(duration) =>
          val timeout = duration.toMillis()
          val _       = underlying.awaitTermination(timeout, TimeUnit.MILLISECONDS)
      })

    def port: Task[Int] = ZIO.attempt(underlying.getPort())

    def shutdown: Task[Unit] = ZIO.attempt(underlying.shutdown()).unit

    def start: Task[Unit] = ZIO.attempt(underlying.start()).unit

    def shutdownNow: Task[Unit] = ZIO.attempt(underlying.shutdownNow()).unit

    def toManaged: ZIO[Scope, Throwable, Service] =
      start.as(this).withFinalizer(_ => this.shutdown.ignore *> this.awaitTermination(None).ignore)

    def toManaged(awaitTermination: Duration): ZIO[Scope, Throwable, Service] =
      start.as(this).withFinalizer(_ => this.shutdown.ignore *> this.awaitTermination(Some(awaitTermination)).ignore)
  }

  @deprecated("Use ManagedServer.fromBuilder", "0.4.0")
  def zmanaged(builder: => ServerBuilder[_]): ZIO[Scope, Throwable, Service] =
    zmanaged(builder, ZIO.succeed(Nil))

  @deprecated("Use ManagedServer.fromServiceList", "0.4.0")
  def zmanaged[R](
      builder: => ServerBuilder[_],
      services: URIO[R, List[ServerServiceDefinition]]
  ): ZIO[R with Scope, Throwable, Service] = ManagedServer.fromServiceList(builder, services)

  @deprecated("Use ManagedServer.fromService", "0.4.0")
  def zmanaged[S0, R0](
      builder: => ServerBuilder[_],
      s0: S0
  )(implicit
      b0: ZBindableService[R0, S0]
  ): ZIO[R0 with Scope, Throwable, Service] =
    ManagedServer.fromServiceList(
      builder,
      ZIO.collectAll(ZBindableService.serviceDefinition(s0) :: Nil)
    )

  @deprecated("Use ManagedServer.fromServices", "0.4.0")
  def zmanaged[
      R0,
      S0,
      R1,
      S1
  ](
      builder: => ServerBuilder[_],
      s0: S0,
      s1: S1
  )(implicit
      b0: ZBindableService[R0, S0],
      b1: ZBindableService[R1, S1]
  ): ZIO[R0 with R1 with Scope, Throwable, Service] =
    ManagedServer.fromServiceList(
      builder,
      ZIO.collectAll(
        ZBindableService.serviceDefinition(s0) ::
          ZBindableService.serviceDefinition(s1) :: Nil
      )
    )

  @deprecated("Use ManagedServer.fromServices", "0.4.0")
  def zmanaged[
      R0,
      S0,
      R1,
      S1,
      R2,
      S2
  ](
      builder: => ServerBuilder[_],
      s0: S0,
      s1: S1,
      s2: S2
  )(implicit
      b0: ZBindableService[R0, S0],
      b1: ZBindableService[R1, S1],
      b2: ZBindableService[R2, S2]
  ): ZIO[R0 with R1 with R2 with Scope, Throwable, Service] =
    ManagedServer.fromServiceList(
      builder,
      ZIO.collectAll(
        ZBindableService.serviceDefinition(s0) ::
          ZBindableService.serviceDefinition(s1) ::
          ZBindableService.serviceDefinition(s2) :: Nil
      )
    )

  def fromManaged(zm: ZIO[Scope, Throwable, Service]) =
    ZLayer.scoped(zm)
}

object ServerLayer {
  def fromServiceList[R](builder: => ServerBuilder[_], l: ServiceList[R]) =
    ZLayer.scoped[R](ManagedServer.fromServiceList(builder, l))

  def access[S1: Tag](
      builder: => ServerBuilder[_]
  )(implicit bs: ZBindableService[Any, S1]): ZLayer[Any with S1, Throwable, Server] =
    fromServiceList(builder, ServiceList.access[S1])

  def accessEnv[R, S1: Tag](
      builder: => ServerBuilder[_]
  )(implicit bs: ZBindableService[R, S1]): ZLayer[R with S1, Throwable, Server] =
    fromServiceList(builder, ServiceList.accessEnv[R, S1])

  def fromServiceLayer[R, S1: Tag](
      serverBuilder: => ServerBuilder[_]
  )(l: ZLayer[R, Throwable, S1])(implicit bs: ZBindableService[Any, S1]) =
    l >>> fromServiceList(serverBuilder, ServiceList.access[S1])

  def fromService[R1, S1](builder: => ServerBuilder[_], s1: S1)(implicit
      bs: ZBindableService[R1, S1]
  ): ZLayer[R1, Throwable, Server] =
    fromServiceList(builder, ServiceList.add(s1))

  def fromServices[R1, S1, R2, S2](builder: => ServerBuilder[_], s1: S1, s2: S2)(implicit
      bs1: ZBindableService[R1, S1],
      bs2: ZBindableService[R2, S2]
  ): ZLayer[R1 with R2, Throwable, Server] =
    fromServiceList(builder, ServiceList.add(s1).add(s2))

  def fromServices[R1, S1, R2, S2, R3, S3](builder: => ServerBuilder[_], s1: S1, s2: S2, s3: S3)(implicit
      bs1: ZBindableService[R1, S1],
      bs2: ZBindableService[R2, S2],
      bs3: ZBindableService[R3, S3]
  ): ZLayer[R1 with R2 with R3, Throwable, Server] =
    fromServiceList(builder, ServiceList.add(s1).add(s2).add(s3))
}

object ManagedServer {
  def fromBuilder(builder: => ServerBuilder[_]): ZIO[Scope, Throwable, Server.Service] =
    fromServiceList(builder, ServiceList)

  def fromService[R1, S1](builder: => ServerBuilder[_], s1: S1)(implicit
      bs: ZBindableService[R1, S1]
  ): ZIO[R1 with Scope, Throwable, Server.Service] =
    fromServiceList(builder, ServiceList.add(s1))

  def fromServices[R1, S1, R2, S2](builder: => ServerBuilder[_], s1: S1, s2: S2)(implicit
      bs1: ZBindableService[R1, S1],
      bs2: ZBindableService[R2, S2]
  ): ZIO[R1 with R2 with Scope, Throwable, Server.Service] =
    fromServiceList(builder, ServiceList.add(s1).add(s2))

  def fromServices[R1, S1, R2, S2, R3, S3](builder: => ServerBuilder[_], s1: S1, s2: S2, s3: S3)(implicit
      bs1: ZBindableService[R1, S1],
      bs2: ZBindableService[R2, S2],
      bs3: ZBindableService[R3, S3]
  ): ZIO[R1 with R2 with R3 with Scope, Throwable, Server.Service] =
    fromServiceList(builder, ServiceList.add(s1).add(s2).add(s3))

  def fromServiceList[R](
      builder: => ServerBuilder[_],
      services: URIO[R, List[ServerServiceDefinition]]
  ): ZIO[R with Scope, Throwable, Server.Service] = fromServiceListScoped[R](builder, services)

  def fromServiceList[R](
      builder: => ServerBuilder[_],
      services: ServiceList[R]
  ): ZIO[R with Scope, Throwable, Server.Service] =
    fromServiceListScoped[R](builder, services.bindAll)

  def fromServiceListScoped[R](
      builder: => ServerBuilder[_],
      services: ZIO[R with Scope, Throwable, List[ServerServiceDefinition]]
  ): ZIO[R with Scope, Throwable, Server.Service] =
    for {
      services0 <- services
      serverImpl = new Server.ServiceImpl(
                     services0
                       .foldLeft(builder) { case (b, s) =>
                         b.addService(s)
                       }
                       .build()
                   )
      server    <- serverImpl.toManaged
    } yield server
}
