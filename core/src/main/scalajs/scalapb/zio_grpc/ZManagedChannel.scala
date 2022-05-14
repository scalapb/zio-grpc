package scalapb.zio_grpc

import io.grpc.ManagedChannel
import zio.ZIO

object ZManagedChannel {
  def apply[R](
      channel: ManagedChannel,
      interceptors: Seq[ZClientInterceptor[R]] = Nil
  ): ZManagedChannel[R] =
    ZIO.succeed(new ZChannel[R](channel, interceptors))

  def apply(channel: ManagedChannel): ZManagedChannel[Any] = apply(channel, Nil)
}
