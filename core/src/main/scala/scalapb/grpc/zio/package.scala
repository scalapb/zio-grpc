package scalapb.grpc

import io.grpc.Status
import _root_.zio.{IO, ZIO, Task}
import _root_.zio.stream.{Stream, ZStream}
import _root_.zio.ZLayer
import _root_.zio.Managed

import io.grpc.ServerBuilder


package object zio {

  import _root_.zio.Has
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
      Managed.make(ZIO.effect(builder.build.start))(
        s => ZIO.effect(s.shutdown).ignore
      ).map(new ServiceImpl(_))

    def fromManaged(zm: Managed[Throwable, io.grpc.Server]) =
    ZLayer.fromManaged(zm.map(s => Has(new ServiceImpl(s))))
  }
}
