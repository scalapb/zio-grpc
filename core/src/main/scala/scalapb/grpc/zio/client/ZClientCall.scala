package scalapb.grpc.zio.client

import io.grpc.ClientCall
import io.grpc.ClientCall.Listener
import io.grpc.Metadata
import scalapb.grpc.zio.GIO

trait ZClientCall[Req, Res] extends Any {
  def start(responseListener: Listener[Res], headers: Metadata): GIO[Unit]

  def request(numMessages: Int): GIO[Unit]

  def cancel(message: String): GIO[Unit]

  def halfClose(): GIO[Unit]

  def sendMessage(message: Req): GIO[Unit]
}

class ZClientCallImpl[Req, Res](private val call: ClientCall[Req, Res])
    extends AnyVal
    with ZClientCall[Req, Res] {
  def start(responseListener: Listener[Res], headers: Metadata): GIO[Unit] =
    GIO.effect(call.start(responseListener, headers))

  def request(numMessages: Int): GIO[Unit] =
    GIO.effect(call.request(numMessages))

  def cancel(message: String): GIO[Unit] =
    GIO.effect(call.cancel(message, null))

  def halfClose(): GIO[Unit] = GIO.effect(call.halfClose())

  def sendMessage(message: Req): GIO[Unit] =
    GIO.effect(call.sendMessage(message))
}

object ZClientCall {
  def apply[Req, Res](call: ClientCall[Req, Res]): ZClientCall[Req, Res] =
    new ZClientCallImpl(call)
}
