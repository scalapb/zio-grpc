package scalapb.zio_grpc

import io.grpc.ManagedChannelBuilder
import zio.ZIO

object ZManagedChannel {
  def apply(
      builder: => ManagedChannelBuilder[_],
      interceptors: Seq[ZClientInterceptor]
  ): ZManagedChannel =
    ZIO.acquireRelease(ZIO.attempt(new ZChannel(builder.build(), interceptors)))(
      _.shutdown().ignore
    )

  def apply(builder: ManagedChannelBuilder[_]): ZManagedChannel = apply(builder, Nil)
}
