package scalapb.zio_grpc.server

import io.grpc.{Metadata, ServerCall, Status}
import zio.ZIO
import scalapb.zio_grpc.GIO
import zio.stm.TSemaphore
import zio.UIO

/** Wrapper around [[io.grpc.ServerCall]] that lifts its effects into ZIO values */
final class ZServerCall[Res](private val call: ServerCall[_, Res], private val canSend: TSemaphore) {
  def isReady: UIO[Boolean] = ZIO.succeed(call.isReady())

  def request(n: Int): GIO[Unit] = GIO.fromTask(ZIO.attempt(call.request(n)))

  // Blocks until the channel is ready to sent.
  // The semaphore being used here gets acquired by users for this class
  // and released by `onReady` within the listener.
  // Marked private for now since the current flow-control implementation is coupled with
  // the listener implementation.
  private[zio_grpc] def awaitReady: UIO[Unit] = canSend.acquire.commit

  def sendMessage(message: Res): GIO[Unit] =
    GIO.fromTask(ZIO.attempt(call.sendMessage(message)))

  def sendHeaders(headers: Metadata): GIO[Unit] =
    GIO.fromTask(ZIO.attempt(call.sendHeaders(headers)))

  def close(status: Status, metadata: Metadata): GIO[Unit] =
    GIO.fromTask(ZIO.attempt(call.close(status, metadata)))

  private[zio_grpc] def setReady(): UIO[Unit] = canSend.release.commit
}
