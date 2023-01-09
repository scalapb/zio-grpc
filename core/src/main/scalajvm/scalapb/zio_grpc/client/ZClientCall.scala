package scalapb.zio_grpc.client

import io.grpc.{ClientCall, StatusException}
import io.grpc.ClientCall.Listener
import scalapb.zio_grpc.GIO
import zio.IO
import scalapb.zio_grpc.SafeMetadata

trait ZClientCall[Req, Res] extends Any {
  self =>
  def start(
      responseListener: Listener[Res],
      headers: SafeMetadata
  ): IO[StatusException, Unit]

  def request(numMessages: Int): IO[StatusException, Unit]

  def cancel(message: String): IO[StatusException, Unit]

  def halfClose(): IO[StatusException, Unit]

  def sendMessage(message: Req): IO[StatusException, Unit]
}

class ZClientCallImpl[Req, Res](private val call: ClientCall[Req, Res]) extends AnyVal with ZClientCall[Req, Res] {
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
  def apply[Req, Res](call: ClientCall[Req, Res]): ZClientCall[Req, Res] =
    new ZClientCallImpl(call)

  class ForwardingZClientCall[Req, Res](
      protected val delegate: ZClientCall[Req, Res]
  ) extends ZClientCall[Req, Res] {
    override def start(
        responseListener: Listener[Res],
        headers: SafeMetadata
    ): IO[StatusException, Unit] = delegate.start(responseListener, headers)

    override def request(numMessages: Int): IO[StatusException, Unit] =
      delegate.request(numMessages)

    override def cancel(message: String): IO[StatusException, Unit] =
      delegate.cancel(message)

    override def halfClose(): IO[StatusException, Unit] = delegate.halfClose()

    override def sendMessage(message: Req): IO[StatusException, Unit] =
      delegate.sendMessage(message)
  }

  def headersTransformer[Req, Res](
      clientCall: ZClientCall[Req, Res],
      updateHeaders: SafeMetadata => IO[StatusException, SafeMetadata]
  ): ZClientCall[Req, Res] =
    new ForwardingZClientCall[Req, Res](clientCall) {
      override def start(
          responseListener: Listener[Res],
          headers: SafeMetadata
      ): IO[StatusException, Unit] =
        updateHeaders(headers) flatMap { h => delegate.start(responseListener, h) }
    }
}
