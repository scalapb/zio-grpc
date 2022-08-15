package scalapb.zio_grpc

import io.grpc.CallOptions
import io.grpc.MethodDescriptor
import scalapb.zio_grpc.client.ZClientCall
import zio.{Task, UIO, ZIO}
import io.grpc.ManagedChannel
import zio.ZEnvironment
import zio.Duration
import java.util.concurrent.TimeUnit

class ZChannel[-R](
    private[zio_grpc] val channel: ManagedChannel,
    interceptors: Seq[ZClientInterceptor[R]]
) {
  def newCall[Req, Res](
      methodDescriptor: MethodDescriptor[Req, Res],
      options: CallOptions
  ): UIO[ZClientCall[R, Req, Res]] = ZIO.succeed(
    interceptors.foldLeft[ZClientCall[R, Req, Res]](
      ZClientCall(channel.newCall(methodDescriptor, options))
    )((call, interceptor) => interceptor.interceptCall(methodDescriptor, options, call))
  )

  def awaitTermination(duration: Duration): Task[Boolean] =
    ZIO.attempt(channel.awaitTermination(duration.toMillis(), TimeUnit.MILLISECONDS))

  def shutdown(): Task[Unit] = ZIO.attempt(channel.shutdown()).unit

  def provideEnvironment(r: ZEnvironment[R]): ZChannel[Any] =
    new ZChannel[Any](channel, interceptors.map(_.provideEnvironment(r)))
}
