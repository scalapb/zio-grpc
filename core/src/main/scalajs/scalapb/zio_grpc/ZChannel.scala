package scalapb.zio_grpc

import zio.ZIO
import io.grpc.ManagedChannel
import zio.Task
import zio.ZEnvironment

class ZChannel[-R](
    private[zio_grpc] val channel: ManagedChannel,
    interceptors: Seq[ZClientInterceptor[R]]
) {
  def shutdown(): Task[Unit] = ZIO.unit

  def provideEnvironment(r: ZEnvironment[R]): ZChannel[Any] =
    new ZChannel[Any](channel, interceptors.map(_.provideEnvironment(r)))
}
