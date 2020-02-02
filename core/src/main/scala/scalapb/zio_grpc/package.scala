package scalapb

import io.grpc.Status
import zio.{IO, ZIO, Task}
import zio.stream.{Stream, ZStream}
import zio.ZLayer
import zio.Managed
import zio.Has

import io.grpc.ServerBuilder

package object zio_grpc {

  type GIO[A] = IO[Status, A]

  type GRIO[R, A] = ZIO[R, Status, A]

  type GStream[A] = Stream[Status, A]

  type GRStream[R, A] = ZStream[R, Status, A]

  type Server = Has[Server.Service]

  object Server {
    trait Service {
      def asEnv = Has(this)

      def port: Task[Int]
    }

    class ServiceImpl(underlying: io.grpc.Server) extends Service {
      def port: Task[Int] = ZIO.effect(underlying.getPort())
    }

    def managed(builder: ServerBuilder[_]): Managed[Throwable, Service] =
      Managed
        .make(ZIO.effect(builder.build.start))(
          s => ZIO.effect(s.shutdown).ignore
        )
        .map(new ServiceImpl(_))

    def fromManaged(zm: Managed[Throwable, io.grpc.Server]) =
      ZLayer.fromManaged(zm.map(s => Has(new ServiceImpl(s))))
  }
}
