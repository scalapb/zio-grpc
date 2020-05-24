package scalapb.zio_grpc

import zio.ZManaged
import io.grpc.ManagedChannelBuilder
import io.grpc.CallOptions
import io.grpc.MethodDescriptor
import scalapb.zio_grpc.client.ZClientCall
import zio.ZIO
import io.grpc.ManagedChannel
import zio.Task

class ZChannel[-R](
    channel: ManagedChannel,
    interceptors: Seq[ZClientInterceptor[R]]
) {
  def newCall[Req, Res](
      methodDescriptor: MethodDescriptor[Req, Res],
      options: CallOptions
  ): ZClientCall[R, Req, Res] =
    interceptors.foldLeft[ZClientCall[R, Req, Res]](
      ZClientCall(channel.newCall(methodDescriptor, options))
    )((call, interceptor) =>
      interceptor.interceptCall(methodDescriptor, options, call)
    )

  def shutdown(): Task[Unit] = ZIO.effect(channel.shutdown()).unit
}

object ZManagedChannel {
  def apply[R](
      builder: ManagedChannelBuilder[_],
      interceptors: Seq[ZClientInterceptor[R]] = Nil
  ): ZManagedChannel[R] =
    ZManaged.make(ZIO.effect(new ZChannel(builder.build(), interceptors)))(
      _.shutdown().ignore
    )

  def apply(builder: ManagedChannelBuilder[_]): ZManagedChannel[Any] =
    ZManaged.make(ZIO.effect(new ZChannel(builder.build(), Nil)))(
      _.shutdown().ignore
    )
}
