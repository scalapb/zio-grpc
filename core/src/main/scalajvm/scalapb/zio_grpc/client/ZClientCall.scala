package scalapb.zio_grpc.client

import io.grpc.ClientCall
import io.grpc.ClientCall.Listener
import scalapb.zio_grpc.GIO
import zio.ZIO
import io.grpc.Status
import scalapb.zio_grpc.SafeMetadata
import zio.ZEnvironment

trait ZClientCall[-R, Req, Res] extends Any {
  self =>
  def start(
      responseListener: Listener[Res],
      headers: SafeMetadata
  ): ZIO[R, Status, Unit]

  def request(numMessages: Int): ZIO[R, Status, Unit]

  def cancel(message: String): ZIO[R, Status, Unit]

  def halfClose(): ZIO[R, Status, Unit]

  def sendMessage(message: Req): ZIO[R, Status, Unit]

  def provideEnvironment(r: => ZEnvironment[R]): ZClientCall[Any, Req, Res] =
    new ZClientCall[Any, Req, Res] {
      def start(
          responseListener: Listener[Res],
          headers: SafeMetadata
      ): ZIO[Any, Status, Unit] =
        self.start(responseListener, headers).provideEnvironment(r)

      def request(numMessages: Int): ZIO[Any, Status, Unit] =
        self.request(numMessages).provideEnvironment(r)

      def cancel(message: String): ZIO[Any, Status, Unit] =
        self.cancel(message).provideEnvironment(r)

      def halfClose(): ZIO[Any, Status, Unit] = self.halfClose().provideEnvironment(r)

      def sendMessage(message: Req): ZIO[Any, Status, Unit] =
        self.sendMessage(message).provideEnvironment(r)
    }
}

class ZClientCallImpl[Req, Res](private val call: ClientCall[Req, Res]) extends AnyVal with ZClientCall[Any, Req, Res] {
  def start(responseListener: Listener[Res], headers: SafeMetadata): GIO[Unit] =
    GIO.attempt(call.start(responseListener, headers.metadata))

  def request(numMessages: Int): GIO[Unit] =
    GIO.attempt(call.request(numMessages))

  def cancel(message: String): GIO[Unit] =
    GIO.attempt(call.cancel(message, null))

  def halfClose(): GIO[Unit] = GIO.attempt(call.halfClose())

  def sendMessage(message: Req): GIO[Unit] =
    GIO.attempt(call.sendMessage(message))
}

object ZClientCall {
  def apply[Req, Res](call: ClientCall[Req, Res]): ZClientCall[Any, Req, Res] =
    new ZClientCallImpl(call)

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
        updateHeaders(headers) flatMap { h => delegate.start(responseListener, h) }
    }
}
