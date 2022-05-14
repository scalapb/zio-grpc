package scalapb.zio_grpc

import io.grpc.ManagedChannelBuilder
import zio.ZIO

object ZManagedChannel {
  def apply[R](
      builder: => ManagedChannelBuilder[_],
      interceptors: Seq[ZClientInterceptor[R]] = Nil
  ): ZManagedChannel[R] =
    ZIO.acquireRelease(ZIO.attempt(new ZChannel(builder.build(), interceptors)))(
      _.shutdown().ignore
    )

  def apply(builder: ManagedChannelBuilder[_]): ZManagedChannel[Any] =
    ZIO.acquireRelease(ZIO.attempt(new ZChannel(builder.build(), Nil)))(
      _.shutdown().ignore
    )
}
