package scalapb.zio_grpc

import zio.{Task, UIO, URIO, ZIO}
import zio.Has
import zio.Managed
import zio.ZLayer
import zio.ZManaged
import zio.Tagged

import io.grpc.ServerBuilder
import io.grpc.ServerServiceDefinition

object Server {
  trait Service {
    def port: Task[Int]

    def shutdown: Task[Unit]

    def shutdownNow: Task[Unit]

    def start: Task[Unit]
  }

  private[zio_grpc] class ServiceImpl(underlying: io.grpc.Server)
      extends Service {
    def port: Task[Int] = ZIO.effect(underlying.getPort())

    def shutdown: Task[Unit] = ZIO.effect(underlying.shutdown()).unit

    def start: Task[Unit] = ZIO.effect(underlying.start()).unit

    def shutdownNow: Task[Unit] = ZIO.effect(underlying.shutdownNow()).unit
  }

  def zmanaged(builder: => ServerBuilder[_]): Managed[Throwable, Service] =
    zmanaged(builder, UIO.succeed(Nil))

  def zmanaged[R](
      builder: => ServerBuilder[_],
      services: URIO[R, List[ServerServiceDefinition]]
  ): ZManaged[R, Throwable, Service] =
    (for {
      services0 <- services
      server = new ServiceImpl(
        services0
          .foldLeft(builder)({
            case (b, s) => b.addService(s)
          })
          .build()
      )
      _ <- server.start
    } yield server).toManaged(_.shutdown.ignore)

  def zmanaged[S0, R0](
      builder: => ServerBuilder[_],
      s0: S0
  )(implicit
      b0: ZBindableService.Aux[S0, R0]
  ): ZManaged[R0, Throwable, Service] =
    zmanaged(
      builder,
      URIO.collectAll(ZBindableService.serviceDefinition(s0) :: Nil)
    )

  def zmanaged[
      S0,
      S1,
      R0,
      R1
  ](
      builder: => ServerBuilder[_],
      s0: S0,
      s1: S1
  )(implicit
      b0: ZBindableService.Aux[S0, R0],
      b1: ZBindableService.Aux[S1, R1]
  ): ZManaged[R0 with R1, Throwable, Service] =
    zmanaged(
      builder,
      URIO.collectAll(
        ZBindableService.serviceDefinition(s0) ::
          ZBindableService.serviceDefinition(s1) :: Nil
      )
    )

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
      b0: ZBindableService.Aux[S0, R0],
      b1: ZBindableService.Aux[S1, R1],
      b2: ZBindableService.Aux[S2, R2]
  ): ZManaged[R0 with R1 with R2, Throwable, Service] =
    zmanaged(
      builder,
      URIO.collectAll(
        ZBindableService.serviceDefinition(s0) ::
          ZBindableService.serviceDefinition(s1) ::
          ZBindableService.serviceDefinition(s2) :: Nil
      )
    )

  def zlive[R0, S0: Tagged](
      builder: => ServerBuilder[_]
  )(implicit
      b0: ZBindableService.Aux[S0, R0]
  ): ZLayer[R0 with Has[S0], Nothing, Server] =
    ZLayer.fromServiceManaged { s0: S0 => Server.zmanaged(builder, s0).orDie }

  def zlive[
      R0,
      S0: Tagged,
      R1,
      S1: Tagged
  ](
      builder: => ServerBuilder[_]
  )(implicit
      b0: ZBindableService.Aux[S0, R0],
      b1: ZBindableService.Aux[S1, R1]
  ): ZLayer[R0 with R1 with Has[S0] with Has[S1], Nothing, Server] =
    ZLayer.fromServicesManaged[S0, S1, R0 with R1, Nothing, Server.Service] {
      (s0: S0, s1: S1) => Server.zmanaged(builder, s0, s1).orDie
    }

  def zlive[
      R0,
      S0: Tagged,
      R1,
      S1: Tagged,
      R2,
      S2: Tagged
  ](
      builder: => ServerBuilder[_]
  )(implicit
      b0: ZBindableService.Aux[S0, R0],
      b1: ZBindableService.Aux[S1, R1],
      b2: ZBindableService.Aux[S2, R2]
  ): ZLayer[R0 with R1 with R2 with Has[S0] with Has[S1] with Has[
    S2
  ], Nothing, Server] =
    ZLayer.fromServicesManaged[
      S0,
      S1,
      S2,
      R0 with R1 with R2,
      Nothing,
      Server.Service
    ]((s0: S0, s1: S1, s2: S2) => Server.zmanaged(builder, s0, s1, s2).orDie)

  def live[S0: Tagged](
      builder: => ServerBuilder[_]
  )(implicit
      b0: ZBindableService.Aux[S0, Any]
  ): ZLayer[Has[S0], Nothing, Server] =
    zlive[Any, S0](builder)

  def live[S0: Tagged, S1: Tagged](
      builder: => ServerBuilder[_]
  )(implicit
      b0: ZBindableService.Aux[S0, Any],
      b1: ZBindableService.Aux[S1, Any]
  ): ZLayer[Has[S0] with Has[S1], Nothing, Server] =
    zlive[Any, S0, Any, S1](builder)

  def live[S0: Tagged, S1: Tagged, S2: Tagged](
      builder: => ServerBuilder[_]
  )(implicit
      b0: ZBindableService.Aux[S0, Any],
      b1: ZBindableService.Aux[S1, Any],
      b2: ZBindableService.Aux[S2, Any]
  ): ZLayer[Has[S0] with Has[S1] with Has[S2], Nothing, Server] =
    zlive[Any, S0, Any, S1, Any, S2](builder)

  def fromManaged(zm: Managed[Throwable, Service]) =
    ZLayer.fromManaged(zm.map(s => Has(s)))
}
