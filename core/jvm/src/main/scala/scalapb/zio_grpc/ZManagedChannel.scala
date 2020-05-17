package scalapb.zio_grpc

import zio.ZManaged
import io.grpc.ManagedChannelBuilder

object ZManagedChannel {
  def apply(builder: ManagedChannelBuilder[_]): ZManagedChannel =
    ZManaged.makeEffect(builder.build())(_.shutdown())
}
