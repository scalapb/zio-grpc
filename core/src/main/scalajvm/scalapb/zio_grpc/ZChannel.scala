package scalapb.zio_grpc

import java.util.concurrent.TimeUnit
import io.grpc.CallOptions
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.MethodDescriptor
import scalapb.zio_grpc.client.ZClientCall
import zio._

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

object ZChannel {

  /** Create a scoped channel that will be shutdown when the scope closes.
    *
    * @param builder
    *   The channel builder to use to create the channel.
    * @param interceptors
    *   The client call interceptors to use.
    * @param timeout
    *   The maximum amount of time to wait for the channel to shutdown.
    * @return
    */
  def scoped[R](
      builder: => ManagedChannelBuilder[_],
      interceptors: Seq[ZClientInterceptor[R]],
      timeout: Duration
  ): RIO[Scope, ZChannel[R]] =
    ZIO
      .acquireRelease(
        ZIO.attempt(new ZChannel[R](builder.build(), interceptors))
      )(channel => channel.shutdown().ignore *> channel.awaitTermination(timeout).ignore)
}
