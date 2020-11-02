package scalapb.zio_grpc

import io.grpc.CallOptions
import io.grpc.MethodDescriptor
import scalapb.zio_grpc.client.ZClientCall
import zio.{Promise, Ref, Task, UIO, ZIO}
import io.grpc.ManagedChannel

class ZChannel[-R](
    private[zio_grpc] val channel: ManagedChannel,
    interceptors: Seq[ZClientInterceptor[R]]
) {
  def newCall[Req, Res](
      methodDescriptor: MethodDescriptor[Req, Res],
      options: CallOptions
  ): GIO[ZClientCall[R, Req, Res]] =
    Ref.make[Option[Promise[Nothing, Unit]]](None).flatMap { readyPromise =>
      GIO.effect {
        interceptors.foldLeft[ZClientCall[R, Req, Res]](
          ZClientCall(channel.newCall(methodDescriptor, options), readyPromise)
        )((call, interceptor) => interceptor.interceptCall(methodDescriptor, options, call))
      }
    }

  def shutdown(): Task[Unit] = ZIO.effect(channel.shutdown()).unit

  def provide(r: R): ZChannel[Any] =
    new ZChannel[Any](channel, interceptors.map(_.provide(r)))
}
