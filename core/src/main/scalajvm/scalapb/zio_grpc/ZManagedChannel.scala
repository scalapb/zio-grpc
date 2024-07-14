package scalapb.zio_grpc

import io.grpc.ManagedChannelBuilder
import zio.ZIO

object ZManagedChannel {
  def apply(
      builder: => ManagedChannelBuilder[?],
      prefetch: Option[Int],
      interceptors: Seq[ZClientInterceptor]
  ): ZManagedChannel = ZIO.acquireRelease(
    ZIO.attempt(new ZChannel(builder.build(), prefetch.map(_.max(1)), interceptors))
  )(_.shutdown().ignore)

  def apply(builder: => ManagedChannelBuilder[?], interceptors: Seq[ZClientInterceptor]): ZManagedChannel =
    apply(builder, None, interceptors)

  def apply(builder: ManagedChannelBuilder[?]): ZManagedChannel =
    apply(builder, None, Nil)
}
