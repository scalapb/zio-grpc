package scalapb.grpc.zio

import zio.ZManaged
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder

object ZManagedChannel {
  def make[R](
      builder: ManagedChannelBuilder[_]
  ): ZManaged[R, Throwable, ManagedChannel] =
    ZManaged.makeEffect(builder.build())(_.shutdown())
}
