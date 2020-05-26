package scalapb.zio_grpc

import zio.ZIO
import io.grpc.ManagedChannel
import zio.Task

class ZChannel[-R](
    private[zio_grpc] val channel: ManagedChannel,
    interceptors: Seq[ZClientInterceptor[R]]
) {
  def shutdown(): Task[Unit] = ZIO.unit

  def provide(r: R): ZChannel[Any] =
    new ZChannel[Any](channel, interceptors.map(_.provide(r)))
}