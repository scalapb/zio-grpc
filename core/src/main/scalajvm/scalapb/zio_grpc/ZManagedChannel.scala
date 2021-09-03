package scalapb.zio_grpc

import io.grpc.ManagedChannelBuilder
import zio.ZIO
import zio.ZManaged

object ZManagedChannel {
  def apply[R](
      builder: => ManagedChannelBuilder[_],
      interceptors: Seq[ZClientInterceptor[R]] = Nil
  ): ZManagedChannel[R] =
    ZManaged.acquireReleaseWith(ZIO.attempt(new ZChannel(builder.build(), interceptors)))(
      _.shutdown().ignore
    )

  def apply(builder: ManagedChannelBuilder[_]): ZManagedChannel[Any] =
    ZManaged.acquireReleaseWith(ZIO.attempt(new ZChannel(builder.build(), Nil)))(
      _.shutdown().ignore
    )
}
