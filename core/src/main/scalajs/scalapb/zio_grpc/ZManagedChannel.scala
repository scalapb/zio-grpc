package scalapb.zio_grpc

import io.grpc.ManagedChannel
import zio.ZIO

object ZManagedChannel {
  def apply(
      channel: ManagedChannel,
      interceptors: Seq[ZClientInterceptor]
  ): ZManagedChannel =
    ZIO.succeed(new ZChannel(channel, interceptors))

  def apply(channel: ManagedChannel): ZManagedChannel = apply(channel, Nil)
}
