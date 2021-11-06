package scalapb.zio_grpc

import zio.{Task, UIO, URIO, ZIO}
import zio.Has
import zio.Managed
import zio.ZLayer
import zio.ZManaged
import zio.Tag

import io.grpc.ServerBuilder
import io.grpc.ServerServiceDefinition

object Server {
  trait Service {
    def port: Task[Int]

    def shutdown: Task[Unit]

    def shutdownNow: Task[Unit]

    def start: Task[Unit]
  }

  private[zio_grpc] class ServiceImpl(underlying: io.grpc.Server) extends Service {
    def port: Task[Int] = ZIO.attempt(underlying.getPort())

    def shutdown: Task[Unit] = ZIO.attempt(underlying.shutdown()).unit

    def start: Task[Unit] = ZIO.attempt(underlying.start()).unit

    def shutdownNow: Task[Unit] = ZIO.attempt(underlying.shutdownNow()).unit

    def toManaged: ZManaged[Any, Throwable, Service] = start.as(this).toManagedWith(_ => this.shutdown.ignore)
  }

  @deprecated("Use ManagedServer.fromBuilder", "0.4.0")
  def zmanaged(builder: => ServerBuilder[_]): Managed[Throwable, Service] =
    zmanaged(builder, UIO.succeed(Nil))

  @deprecated("Use ManagedServer.fromServiceList", "0.4.0")
  def zmanaged[R](
      builder: => ServerBuilder[_],
      services: URIO[R, List[ServerServiceDefinition]]
  ): ZManaged[R, Throwable, Service] = ManagedServer.fromServiceList(builder, services)

  @deprecated("Use ManagedServer.fromService", "0.4.0")
  def zmanaged[S0, R0](
      builder: => ServerBuilder[_],
      s0: S0
  )(implicit
      b0: ZBindableService[R0, S0]
  ): ZManaged[R0, Throwable, Service] =
    ManagedServer.fromServiceList(
      builder,
      URIO.collectAll(ZBindableService.serviceDefinition(s0) :: Nil)
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
  ): ZManaged[R0 with R1, Throwable, Service] =
    ManagedServer.fromServiceList(
      builder,
      URIO.collectAll(
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
  ): ZManaged[R0 with R1 with R2, Throwable, Service] =
    ManagedServer.fromServiceList(
      builder,
      URIO.collectAll(
        ZBindableService.serviceDefinition(s0) ::
          ZBindableService.serviceDefinition(s1) ::
          ZBindableService.serviceDefinition(s2) :: Nil
      )
    )

  @deprecated("Use ServerLayer.fromService", "0.4.0")
  def zlive[R0, S0: Tag](
      builder: => ServerBuilder[_]
  )(implicit
      b0: ZBindableService[R0, S0]
  ): ZLayer[R0 with Has[S0], Throwable, Server] =
    ManagedServer.fromBuilder(builder).toLayer

  @deprecated("Use ServerLayer.fromServices", "0.4.0")
  def zlive[
      R0,
      S0: Tag,
      R1,
      S1: Tag
  ](
      builder: => ServerBuilder[_]
  )(implicit
      b0: ZBindableService[R0, S0],
      b1: ZBindableService[R1, S1]
  ): ZLayer[R0 with R1 with Has[S0] with Has[S1], Throwable, Server] =
    ZLayer.fromServicesManaged[S0, S1, R0 with R1, Throwable, Server.Service] { (s0: S0, s1: S1) =>
      ManagedServer.fromServices(builder, s0, s1)
    }

  @deprecated("Use ServerLayer.fromServices", "0.4.0")
  def zlive[
      R0,
      S0: Tag,
      R1,
      S1: Tag,
      R2,
      S2: Tag
  ](
      builder: => ServerBuilder[_]
  )(implicit
      b0: ZBindableService[R0, S0],
      b1: ZBindableService[R1, S1],
      b2: ZBindableService[R2, S2]
  ): ZLayer[R0 with R1 with R2 with Has[S0] with Has[S1] with Has[
    S2
  ], Throwable, Server] =
    ZLayer.fromServicesManaged[
      S0,
      S1,
      S2,
      R0 with R1 with R2,
      Throwable,
      Server.Service
    ]((s0: S0, s1: S1, s2: S2) => Server.zmanaged(builder, s0, s1, s2))

  @deprecated("Use ServerLayer.access", "0.4.0")
  def live[S0: Tag](
      builder: => ServerBuilder[_]
  )(implicit
      b0: ZBindableService[Any, S0]
  ): ZLayer[Has[S0], Throwable, Server] =
    zlive[Any, S0](builder)

  @deprecated("Use ServerLayer.fromServiceList(ServiceList.access[S0].access[S1])", "0.4.0")
  def live[S0: Tag, S1: Tag](
      builder: => ServerBuilder[_]
  )(implicit
      b0: ZBindableService[Any, S0],
      b1: ZBindableService[Any, S1]
  ): ZLayer[Has[S0] with Has[S1], Throwable, Server] =
    zlive[Any, S0, Any, S1](builder)

  @deprecated("Use ServerLayer.fromServiceList(ServiceList.access[S0].access[S1].access[S2])", "0.4.0")
  def live[S0: Tag, S1: Tag, S2: Tag](
      builder: => ServerBuilder[_]
  )(implicit
      b0: ZBindableService[Any, S0],
      b1: ZBindableService[Any, S1],
      b2: ZBindableService[Any, S2]
  ): ZLayer[Has[S0] with Has[S1] with Has[S2], Throwable, Server] =
    zlive[Any, S0, Any, S1, Any, S2](builder)

  def fromManaged(zm: Managed[Throwable, Service]) =
    ZLayer.fromManaged(zm.map(s => Has(s)))
}

object ServerLayer {
  def fromServiceList[R](builder: => ServerBuilder[_], l: ServiceList[R]) =
    ManagedServer.fromServiceList(builder, l).toLayer

  def access[S1: Tag](
      builder: => ServerBuilder[_]
  )(implicit bs: ZBindableService[Any, S1]): ZLayer[Any with Has[S1], Throwable, Server] =
    fromServiceList(builder, ServiceList.access[S1])

  def accessEnv[R, S1: Tag](
      builder: => ServerBuilder[_]
  )(implicit bs: ZBindableService[R, S1]): ZLayer[R with Has[S1], Throwable, Server] =
    fromServiceList(builder, ServiceList.accessEnv[R, S1])

  def fromServiceLayer[R, S1: Tag](
      serverBuilder: => ServerBuilder[_]
  )(l: ZLayer[R, Throwable, Has[S1]])(implicit bs: ZBindableService[Any, S1]) =
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
  def fromBuilder(builder: => ServerBuilder[_]): ZManaged[Any, Throwable, Server.Service] =
    fromServiceList(builder, ServiceList)

  def fromService[R1, S1](builder: => ServerBuilder[_], s1: S1)(implicit
      bs: ZBindableService[R1, S1]
  ): ZManaged[R1, Throwable, Server.Service] =
    fromServiceList(builder, ServiceList.add(s1))

  def fromServices[R1, S1, R2, S2](builder: => ServerBuilder[_], s1: S1, s2: S2)(implicit
      bs1: ZBindableService[R1, S1],
      bs2: ZBindableService[R2, S2]
  ): ZManaged[R1 with R2, Throwable, Server.Service] =
    fromServiceList(builder, ServiceList.add(s1).add(s2))

  def fromServices[R1, S1, R2, S2, R3, S3](builder: => ServerBuilder[_], s1: S1, s2: S2, s3: S3)(implicit
      bs1: ZBindableService[R1, S1],
      bs2: ZBindableService[R2, S2],
      bs3: ZBindableService[R3, S3]
  ): ZManaged[R1 with R2 with R3, Throwable, Server.Service] =
    fromServiceList(builder, ServiceList.add(s1).add(s2).add(s3))

  def fromServiceList[R](
      builder: => ServerBuilder[_],
      services: URIO[R, List[ServerServiceDefinition]]
  ): ZManaged[R, Throwable, Server.Service] = fromServiceList(builder, services.toManaged)

  def fromServiceList[R](
      builder: => ServerBuilder[_],
      services: ServiceList[R]
  ): ZManaged[R, Throwable, Server.Service] =
    fromServiceList(builder, services.bindAll)

  def fromServiceList[R](
      builder: => ServerBuilder[_],
      services: ZManaged[R, Throwable, List[ServerServiceDefinition]]
  ): ZManaged[R, Throwable, Server.Service] =
    for {
      services0 <- services
      serverImpl = new Server.ServiceImpl(
                     services0
                       .foldLeft(builder)({ case (b, s) =>
                         b.addService(s)
                       })
                       .build()
                   )
      server    <- serverImpl.toManaged
    } yield server
}
