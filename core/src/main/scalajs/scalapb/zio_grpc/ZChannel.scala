package scalapb.zio_grpc

import zio.ZIO
import io.grpc.ManagedChannel
import zio.Task

class ZChannel(
    private[zio_grpc] val channel: ManagedChannel,
    private[zio_grpc] val prefetch: Option[Int],
    interceptors: Seq[ZClientInterceptor]
) {
  def this(channel: ManagedChannel, interceptors: Seq[ZClientInterceptor]) =
    this(channel, None, interceptors)

  def shutdown(): Task[Unit] = ZIO.unit
}
