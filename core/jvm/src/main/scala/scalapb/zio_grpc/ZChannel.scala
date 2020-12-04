package scalapb.zio_grpc

import io.grpc.CallOptions
import io.grpc.MethodDescriptor
import scalapb.zio_grpc.client.ZClientCall
import zio.{Promise, Ref, Semaphore, Task, ZIO}
import io.grpc.ManagedChannel

class ZChannel[-R](
    private[zio_grpc] val channel: ManagedChannel,
    interceptors: Seq[ZClientInterceptor[R]]
) {
  def newCall[Req, Res](
      methodDescriptor: MethodDescriptor[Req, Res],
      options: CallOptions
  ): GIO[ZClientCall[R, Req, Res]] = for {
    readyPromise    <- Ref.make[Option[Promise[Nothing, Unit]]](None)
    readySync       <- Semaphore.make(1)
    call             = ZClientCall(channel.newCall(methodDescriptor, options), readyPromise, readySync)
    interceptedCall <- GIO.effect {
                         interceptors.foldLeft[ZClientCall[R, Req, Res]](call)((call, interceptor) =>
                           interceptor.interceptCall(methodDescriptor, options, call)
                         )
                       }
  } yield interceptedCall

  def shutdown(): Task[Unit] = ZIO.effect(channel.shutdown()).unit

  def provide(r: R): ZChannel[Any] =
    new ZChannel[Any](channel, interceptors.map(_.provide(r)))
}
