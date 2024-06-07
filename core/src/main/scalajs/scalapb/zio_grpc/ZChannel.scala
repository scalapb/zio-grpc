package scalapb.zio_grpc

import zio.ZIO
import io.grpc.ManagedChannel
import zio.Task

class ZChannel(
    private[zio_grpc] val channel: ManagedChannel,
    interceptors: Seq[ZClientInterceptor]
) {
  def shutdown(): Task[Unit] = ZIO.unit
}
