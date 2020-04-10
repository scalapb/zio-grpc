package scalapb

import io.grpc.Status
import zio.{IO, ZIO, Task}
import zio.stream.Stream
import zio.Layer
import zio.ZLayer
import zio.Managed
import zio.Has
import zio.Tagged
import zio.ZManaged

import io.grpc.ServerBuilder
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder

package object zio_grpc {

  import io.grpc.ServerServiceDefinition

  import zio.UIO

  type GIO[A] = IO[Status, A]

  type GStream[A] = Stream[Status, A]

  type Server = Has[Server.Service]

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

    def managed(builder: => ServerBuilder[_]): Managed[Throwable, Service] =
      managed(builder, UIO.succeed(Nil))

    def managed(
        builder: => ServerBuilder[_],
        services: UIO[List[ServerServiceDefinition]]
    ): Managed[Throwable, Service] = {
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
    }

    def managed[S0: ZBindableService](
        builder: => ServerBuilder[_],
        s0: S0
    ): Managed[Throwable, Service] =
      managed(
        builder,
        UIO.collectAll(ZBindableService.serviceDefinition(s0) :: Nil)
      )

    def managed[
        S0: ZBindableService,
        S1: ZBindableService
    ](
        builder: => ServerBuilder[_],
        s0: S0,
        s1: S1
    ): Managed[Throwable, Service] =
      managed(
        builder,
        UIO.collectAll(
          ZBindableService.serviceDefinition(s0) ::
            ZBindableService.serviceDefinition(s1) :: Nil
        )
      )

    def managed[
        S0: ZBindableService,
        S1: ZBindableService,
        S2: ZBindableService
    ](
        builder: => ServerBuilder[_],
        s0: S0,
        s1: S1,
        s2: S2
    ): Managed[Throwable, Service] =
      managed(
        builder,
        UIO.collectAll(
          ZBindableService.serviceDefinition(s0) ::
            ZBindableService.serviceDefinition(s1) ::
            ZBindableService.serviceDefinition(s2) :: Nil
        )
      )

    def live[S0: Tagged: ZBindableService](
        builder: => ServerBuilder[_]
    ): ZLayer[Has[S0], Nothing, Server] =
      ZLayer.fromServiceManaged { s0: S0 => Server.managed(builder, s0).orDie }

    def live[
        S0: Tagged: ZBindableService,
        S1: Tagged: ZBindableService
    ](
        builder: => ServerBuilder[_]
    ): ZLayer[Has[S0] with Has[S1], Nothing, Server] =
      ZLayer.fromServicesManaged[S0, S1, Any, Nothing, Server.Service] {
        (s0: S0, s1: S1) => Server.managed(builder, s0, s1).orDie
      }

    def live[
        S0: Tagged: ZBindableService,
        S1: Tagged: ZBindableService,
        S2: Tagged: ZBindableService
    ](
        builder: => ServerBuilder[_]
    ): ZLayer[Has[S0] with Has[S1] with Has[S2], Nothing, Server] =
      ZLayer.fromServicesManaged[S0, S1, S2, Any, Nothing, Server.Service] {
        (s0: S0, s1: S1, s2: S2) => Server.managed(builder, s0, s1, s2).orDie
      }

    def fromManaged(zm: Managed[Throwable, Service]) =
      ZLayer.fromManaged(zm.map(s => Has(s)))
  }

  type ZManagedChannel = Managed[Throwable, ManagedChannel]
  object ZManagedChannel {
    def apply(builder: ManagedChannelBuilder[_]): ZManagedChannel =
      ZManaged.makeEffect(builder.build())(_.shutdown())
  }
}
