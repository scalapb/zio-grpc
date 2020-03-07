package scalapb

import io.grpc.Status
import zio.{IO, ZIO, Task}
import zio.stream.Stream
import zio.ZLayer
import zio.Managed
import zio.Has
import zio.Tagged
import zio.ZManaged

import io.grpc.ServerBuilder
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder

package object zio_grpc {

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

    def managed[S0](builder: => ServerBuilder[_], service0: S0)(
        implicit b0: ZBindableService[S0]
    ): Managed[Throwable, Service] =
      (for {
        ssd0 <- b0.bindService(service0)
        ourBuilder = builder.addService(ssd0)
        server = new ServiceImpl(ourBuilder.build())
        _ <- server.start
      } yield server).toManaged(_.shutdown.ignore)

    def live[S0: Tagged](
        builder: => ServerBuilder[_]
    )(
        implicit b0: ZBindableService[S0]
    ): ZLayer[Has[S0], Nothing, Server] =
      ZLayer.fromServiceManaged { s0: S0 => Server.managed(builder, s0).orDie }

    def fromManaged(zm: Managed[Throwable, io.grpc.Server]) =
      ZLayer.fromManaged(zm.map(s => Has(new ServiceImpl(s))))
  }

  type ZManagedChannel = Managed[Throwable, ManagedChannel]
  object ZManagedChannel {
    def apply(builder: ManagedChannelBuilder[_]): ZManagedChannel =
      ZManaged.makeEffect(builder.build())(_.shutdown())
  }
}
