package scalapb.zio_grpc.client

import io.grpc.ClientCall
import io.grpc.ClientCall.Listener
import scalapb.zio_grpc.{GIO, SafeMetadata, ZCall, ZCallBase}
import zio.{Semaphore, ZIO}
import io.grpc.Status
import scalapb.zio_grpc.ZCall.ReadyPromise

trait ZClientCall[-R, Req, Res] extends ZCall[R, Req] {
  self =>
  def start(
      responseListener: Listener[Res],
      headers: SafeMetadata
  ): ZIO[R, Status, Unit]

  def cancel(message: String): ZIO[R, Status, Unit]

  def halfClose(): ZIO[R, Status, Unit]

  def provide(r: R): ZClientCall[Any, Req, Res] =
    new ZClientCall[Any, Req, Res] {
      def start(
          responseListener: Listener[Res],
          headers: SafeMetadata
      ): ZIO[Any, Status, Unit] =
        self.start(responseListener, headers).provide(r)

      def request(numMessages: Int): ZIO[Any, Status, Unit] =
        self.request(numMessages).provide(r)

      def cancel(message: String): ZIO[Any, Status, Unit] =
        self.cancel(message).provide(r)

      def halfClose(): ZIO[Any, Status, Unit] = self.halfClose().provide(r)

      def sendMessage(message: Req): ZIO[Any, Status, Unit] =
        self.sendMessage(message).provide(r)

      override def sendMessageWhenReady(message: Req): ZIO[Any, Status, Unit] =
        self.sendMessageWhenReady(message).provide(r)

      override def onReady(): ZIO[Any, Status, Unit] =
        self.onReady().provide(r)
    }
}

class ZClientCallImpl[Req, Res](
    private val call: ClientCall[Req, Res],
    private[zio_grpc] val readyPromise: ReadyPromise,
    private[zio_grpc] val readySync: Semaphore
) extends ZClientCall[Any, Req, Res]
    with ZCallBase[Any, Req] {
  def start(responseListener: Listener[Res], headers: SafeMetadata): GIO[Unit] =
    GIO.effect(call.start(responseListener, headers.metadata))

  def request(numMessages: Int): GIO[Unit] =
    GIO.effect(call.request(numMessages))

  def cancel(message: String): GIO[Unit] =
    GIO.effect(call.cancel(message, null))

  def halfClose(): GIO[Unit] = GIO.effect(call.halfClose())

  def sendMessage(message: Req): GIO[Unit] =
    GIO.effect(call.sendMessage(message))

  private[zio_grpc] val isReady: GIO[Boolean] =
    GIO.effect(call.isReady)
}

object ZClientCall {
  def apply[Req, Res](
      call: ClientCall[Req, Res],
      readyPromise: ReadyPromise,
      readySync: Semaphore
  ): ZClientCall[Any, Req, Res] =
    new ZClientCallImpl(call, readyPromise, readySync)

  class ForwardingZClientCall[R, Req, Res, R1 >: R](
      protected val delegate: ZClientCall[R1, Req, Res]
  ) extends ZClientCall[R, Req, Res] {
    override def start(
        responseListener: Listener[Res],
        headers: SafeMetadata
    ): ZIO[R, Status, Unit] = delegate.start(responseListener, headers)

    override def request(numMessages: Int): ZIO[R, Status, Unit] =
      delegate.request(numMessages)

    override def cancel(message: String): ZIO[R, Status, Unit] =
      delegate.cancel(message)

    override def halfClose(): ZIO[R, Status, Unit] = delegate.halfClose()

    override def sendMessage(message: Req): ZIO[R, Status, Unit] =
      delegate.sendMessage(message)

    override def sendMessageWhenReady(message: Req): ZIO[R, Status, Unit] =
      delegate.sendMessageWhenReady(message)

    override def onReady(): ZIO[R, Status, Unit] =
      delegate.onReady()
  }

  def headersTransformer[R, Req, Res](
      clientCall: ZClientCall[R, Req, Res],
      updateHeaders: SafeMetadata => ZIO[R, Status, SafeMetadata]
  ): ZClientCall[R, Req, Res] =
    new ForwardingZClientCall[R, Req, Res, R](clientCall) {
      override def start(
          responseListener: Listener[Res],
          headers: SafeMetadata
      ): ZIO[R, Status, Unit] =
        updateHeaders(headers) >>= { h => delegate.start(responseListener, h) }
    }
}
