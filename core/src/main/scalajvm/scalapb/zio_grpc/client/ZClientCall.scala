package scalapb.zio_grpc.client

import io.grpc.{ClientCall, StatusRuntimeException}
import io.grpc.ClientCall.Listener
import scalapb.zio_grpc.GIO
import zio.IO
import scalapb.zio_grpc.SafeMetadata

trait ZClientCall[Req, Res] extends Any {
  self =>
  def start(
      responseListener: Listener[Res],
      headers: SafeMetadata
  ): IO[StatusRuntimeException, Unit]

  def request(numMessages: Int): IO[StatusRuntimeException, Unit]

  def cancel(message: String): IO[StatusRuntimeException, Unit]

  def halfClose(): IO[StatusRuntimeException, Unit]

  def sendMessage(message: Req): IO[StatusRuntimeException, Unit]
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
    ): IO[StatusRuntimeException, Unit] = delegate.start(responseListener, headers)

    override def request(numMessages: Int): IO[StatusRuntimeException, Unit] =
      delegate.request(numMessages)

    override def cancel(message: String): IO[StatusRuntimeException, Unit] =
      delegate.cancel(message)

    override def halfClose(): IO[StatusRuntimeException, Unit] = delegate.halfClose()

    override def sendMessage(message: Req): IO[StatusRuntimeException, Unit] =
      delegate.sendMessage(message)
  }

  def headersTransformer[Req, Res](
      clientCall: ZClientCall[Req, Res],
      updateHeaders: SafeMetadata => IO[StatusRuntimeException, SafeMetadata]
  ): ZClientCall[Req, Res] =
    new ForwardingZClientCall[Req, Res](clientCall) {
      override def start(
          responseListener: Listener[Res],
          headers: SafeMetadata
      ): IO[StatusRuntimeException, Unit] =
        updateHeaders(headers) flatMap { h => delegate.start(responseListener, h) }
    }
}
