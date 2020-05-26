package scalapb.zio_grpc

import zio.ZManaged
import io.grpc.ManagedChannel

object ZManagedChannel {
  def apply[R](
      channel: ManagedChannel,
      interceptors: Seq[ZClientInterceptor[R]] = Nil
  ): ZManagedChannel[R] =
    ZManaged.succeed(new ZChannel[R](channel, interceptors))

  def apply(channel: ManagedChannel): ZManagedChannel[Any] = apply(channel, Nil)
}
