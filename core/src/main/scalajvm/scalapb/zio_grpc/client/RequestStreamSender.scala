package scalapb.zio_grpc.client

import io.grpc.{ClientCall, ForwardingClientCallListener, Metadata, Status, StatusException}
import zio._
import zio.stream.ZStream

/** Outbound flow control, allocated only for calls with streaming requests. */
private[client] final class RequestStreamSender[Req, Res](
    call: ZClientCall[Req, Res],
    responseListener: ClientCall.Listener[Res],
    runtime: Runtime[Any],
    ready: Queue[Unit],
    closed: Promise[Nothing, Unit]
) extends ForwardingClientCallListener.SimpleForwardingClientCallListener[Res](responseListener) {
  private def unsafeRun(effect: UIO[Any]): Unit =
    Unsafe.unsafe { implicit unsafe =>
      runtime.unsafe.run(effect).getOrThrowFiberFailure()
      ()
    }

  override def onReady(): Unit = unsafeRun(ready.offer(()))

  override def onClose(status: Status, trailers: Metadata): Unit = {
    // Stop input independently of response consumption, including blocked input acquisition.
    unsafeRun(closed.succeed(()))
    responseListener.onClose(status, trailers)
  }

  private def sendMessage(message: Req): IO[StatusException, Unit] =
    call.isReady.flatMap {
      case true  => call.sendMessage(message)
      // Notifications remain queued even if onReady runs between isReady and take.
      // Stale or coalesced notifications are harmless: always recheck readiness.
      case false => ready.take *> sendMessage(message)
    }

  def send(input: ZStream[Any, StatusException, Req]): IO[StatusException, Unit] =
    (input.runForeach(sendMessage) *> call.halfClose().unlessZIO(closed.isDone)).unit
      .raceFirst(closed.await)
      .unlessZIO(closed.isDone)
      .unit
}

private[client] object RequestStreamSender {
  def make[Req, Res](call: ZClientCall[Req, Res], listener: ClientCall.Listener[Res]) =
    for {
      runtime <- ZIO.runtime[Any]
      ready   <- Queue.sliding[Unit](1)
      closed  <- Promise.make[Nothing, Unit]
    } yield new RequestStreamSender(call, listener, runtime, ready, closed)
}
