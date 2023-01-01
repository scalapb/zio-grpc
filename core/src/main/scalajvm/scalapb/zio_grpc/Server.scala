package scalapb.zio_grpc

import zio.{Duration, Scope, Tag, Task, URIO, ZIO, ZLayer}
import io.grpc.ServerBuilder
import io.grpc.ServerServiceDefinition
import java.util.concurrent.TimeUnit

trait Server {
  def awaitTermination: Task[Unit]

  def awaitTermination(duratio: Duration): Task[Boolean]

  def port: Task[Int]

  def shutdown: Task[Unit]

  def shutdownNow: Task[Unit]

  def start: Task[Unit]
}

private[zio_grpc] class ServerImpl(underlying: io.grpc.Server) extends Server {
  val awaitTermination: Task[Unit] = ZIO.attempt(underlying.awaitTermination())

  def awaitTermination(duration: Duration): Task[Boolean] =
    ZIO.attempt(underlying.awaitTermination(duration.toMillis(), TimeUnit.MILLISECONDS))

  def port: Task[Int] = ZIO.attempt(underlying.getPort())

  def shutdown: Task[Unit] = ZIO.attempt(underlying.shutdown()).unit

  def start: Task[Unit] = ZIO.attempt(underlying.start()).unit

  def shutdownNow: Task[Unit] = ZIO.attempt(underlying.shutdownNow()).unit

  def toManaged: ZIO[Scope, Throwable, Server] =
    start.as(this).withFinalizer(_ => this.shutdown.ignore)
}

object Server {
  def fromScoped(zm: ZIO[Scope, Throwable, Server]): ZLayer[Any, Throwable, Server] =
    ZLayer.scoped[Any](zm)
}

object ServerLayer {
  def fromServiceList[R](builder: => ServerBuilder[_], l: ServiceList[R]): ZLayer[R, Throwable, Server] =
    ZLayer.scoped[R](ScopedServer.fromServiceList(builder, l))

  def fromEnvironment[S1: Tag: ZBindableService](
      builder: => ServerBuilder[_]
  ): ZLayer[S1, Throwable, Server] =
    fromServiceList(builder, ServiceList.addFromEnvironment[S1])

  def fromService[S1: ZBindableService](builder: => ServerBuilder[_], s1: S1): ZLayer[Any, Throwable, Server] =
    fromServiceList(builder, ServiceList.add(s1))

  def fromServiceLayer[R, S1: Tag: ZBindableService](
      serverBuilder: => ServerBuilder[_]
  )(l: ZLayer[R, Throwable, S1]): ZLayer[R, Throwable, Server] =
    l.flatMap(env => fromService(serverBuilder, env.get[S1]))

  def fromServices[S1: ZBindableService, S2: ZBindableService](
      builder: => ServerBuilder[_],
      s1: S1,
      s2: S2
  ): ZLayer[Any, Throwable, Server] =
    fromServiceList(builder, ServiceList.add(s1).add(s2))

  def fromServices[S1: ZBindableService, S2: ZBindableService, S3: ZBindableService](
      builder: => ServerBuilder[_],
      s1: S1,
      s2: S2,
      s3: S3
  ): ZLayer[Any, Throwable, Server] =
    fromServiceList(builder, ServiceList.add(s1).add(s2).add(s3))
}

object ScopedServer {
  def fromBuilder(builder: => ServerBuilder[_]): ZIO[Scope, Throwable, Server] =
    fromServiceList(builder, ServiceList)

  def fromService[S1](builder: => ServerBuilder[_], s1: S1)(implicit
      bs: ZBindableService[S1]
  ): ZIO[Scope, Throwable, Server] =
    fromServiceList(builder, ServiceList.add(s1))

  def fromServices[S1: ZBindableService, S2: ZBindableService](
      builder: => ServerBuilder[_],
      s1: S1,
      s2: S2
  ): ZIO[Scope, Throwable, Server] =
    fromServiceList(builder, ServiceList.add(s1).add(s2))

  def fromServices[S1: ZBindableService, S2: ZBindableService, S3: ZBindableService](
      builder: => ServerBuilder[_],
      s1: S1,
      s2: S2,
      s3: S3
  ): ZIO[Scope, Throwable, Server] =
    fromServiceList(builder, ServiceList.add(s1).add(s2).add(s3))

  def fromServiceList[R](
      builder: => ServerBuilder[_],
      services: URIO[R, List[ServerServiceDefinition]]
  ): ZIO[R with Scope, Throwable, Server] = fromServiceListScoped[R](builder, services)

  def fromServiceList[R](
      builder: => ServerBuilder[_],
      services: ServiceList[R]
  ): ZIO[R with Scope, Throwable, Server] =
    fromServiceListScoped[R](builder, services.bindAll)

  def fromServiceListScoped[R](
      builder: => ServerBuilder[_],
      services: ZIO[R with Scope, Throwable, List[ServerServiceDefinition]]
  ): ZIO[R with Scope, Throwable, Server] =
    for {
      services0 <- services
      serverImpl = new ServerImpl(
                     services0
                       .foldLeft(builder) { case (b, s) =>
                         b.addService(s)
                       }
                       .build()
                   )
      server    <- serverImpl.toManaged
    } yield server
}
