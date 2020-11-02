package scalapb.zio_grpc.server

import io.grpc.{Metadata, ServerCall, Status}
import zio.ZIO
import scalapb.zio_grpc.{GIO, ZCall}
import scalapb.zio_grpc.ZCall.ReadyPromise

/** Wrapper around [[io.grpc.ServerCall]] that lifts its effects into ZIO values */
class ZServerCall[Res](private val call: ServerCall[_, Res], private[zio_grpc] val readyPromise: ReadyPromise)
    extends ZCall[Any, Res] {
  def request(n: Int): GIO[Unit] = GIO.effect(call.request(n))

  def sendMessage(message: Res): GIO[Unit] =
    GIO.effect(call.sendMessage(message))

  def sendHeaders(headers: Metadata): GIO[Unit] =
    GIO.effect(call.sendHeaders(headers))

  def close(status: Status, metadata: Metadata): GIO[Unit] =
    GIO.effect(call.close(status, metadata))

  private[zio_grpc] def isReady: ZIO[Any, Status, Boolean] =
    GIO.effect(call.isReady)
}
