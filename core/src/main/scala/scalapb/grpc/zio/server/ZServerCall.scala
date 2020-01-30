package scalapb.grpc.zio.server

import io.grpc.{ServerCall, Metadata, Status}
import zio.ZIO
import zio.UIO
import scalapb.grpc.zio.GIO

trait ZServerCall[Res] extends Any {
  def request(n: Int): GIO[Unit]

  def sendMessage(message: Res): GIO[Unit]

  def sendHeaders(headers: Metadata): GIO[Unit]

  def close(status: Status, metadata: Metadata): GIO[Unit]
}

class ZServerCallImpl[Req, Res](private val call: ServerCall[Req, Res])
    extends AnyVal
    with ZServerCall[Res] {
  def request(n: Int): GIO[Unit] = GIO.fromTask(ZIO.effect(call.request(n)))

  def sendMessage(message: Res): GIO[Unit] =
    GIO.fromTask(ZIO.effect(call.sendMessage(message)))

  def sendHeaders(headers: Metadata): GIO[Unit] =
    GIO.fromTask(ZIO.effect(call.sendHeaders(headers)))

  def close(status: Status, metadata: Metadata): GIO[Unit] =
    GIO.fromTask(ZIO.effect(call.close(status, metadata)))
}

object ZServerCall {
  def makeFrom[Req, Res](call: ServerCall[Req, Res]): UIO[ZServerCall[Res]] =
    ZIO.succeed(new ZServerCallImpl(call))
}
