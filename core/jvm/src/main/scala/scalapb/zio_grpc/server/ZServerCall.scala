package scalapb.zio_grpc.server

import io.grpc.{Metadata, ServerCall, Status}
import zio.ZIO
import scalapb.zio_grpc.GIO
import scalapb.zio_grpc.SafeMetadata

/** Wrapper around [[io.grpc.ServerCall]] that lifts its effects into ZIO values */
class ZServerCall[Res](private val call: ServerCall[_, Res]) extends AnyVal {
  def request(n: Int): GIO[Unit] = GIO.fromTask(ZIO.effect(call.request(n)))

  def sendMessage(message: Res): GIO[Unit] =
    GIO.fromTask(ZIO.effect(call.sendMessage(message)))

  def sendHeaders(headers: Metadata): GIO[Unit] =
    GIO.fromTask(ZIO.effect(call.sendHeaders(headers)))

  def sendHeaders(headers: SafeMetadata): GIO[Unit] =
    sendHeaders(headers.metadata)

  def close(status: Status, metadata: Metadata): GIO[Unit] =
    GIO.fromTask(ZIO.effect(call.close(status, metadata)))
}
