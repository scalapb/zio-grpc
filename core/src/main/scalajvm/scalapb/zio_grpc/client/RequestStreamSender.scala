package scalapb.zio_grpc.client

import io.grpc.{ClientCall, ForwardingClientCallListener, Metadata, Status, StatusException}
import zio._
import zio.stream.ZStream

/** Outbound flow control, allocated only for calls with streaming requests. */
private[client] final class RequestStreamSender[Req, Res](
    call: ZClientCall[Req, Res],
    responseListener: ClientCall.Listener[Res],
    runtime: Runtime[Any],
    ready: Ref[Promise[Nothing, Unit]],
    closed: Promise[Nothing, Unit]
) {
  val listener: ClientCall.Listener[Res] =
    new ForwardingClientCallListener.SimpleForwardingClientCallListener[Res](responseListener) {
      override def onReady(): Unit =
        Unsafe.unsafe { implicit unsafe =>
          runtime.unsafe
            .run(Promise.make[Nothing, Unit].flatMap(next => ready.getAndSet(next).flatMap(_.succeed(()))))
            .getOrThrowFiberFailure()
          ()
        }

      override def onClose(status: Status, trailers: Metadata): Unit = {
        // Stop even an input blocked on its next element, independently of response consumption.
        Unsafe.unsafe { implicit unsafe =>
          runtime.unsafe.run(closed.succeed(())).getOrThrowFiberFailure()
          ()
        }
        responseListener.onClose(status, trailers)
      }
    }

  private def sendMessage(message: Req): IO[StatusException, Unit] =
    // Capture the notification BEFORE checking readiness: an onReady between these two
    // operations must complete the promise we wait on, rather than get lost.
    closed.isDone.flatMap {
      // The enclosing race with closed.await interrupts this wait; never drain more input.
      case true  => ZIO.never
      case false =>
        ready.get.flatMap { signal =>
          call.isReady.flatMap {
            case true  => call.sendMessage(message)
            case false => signal.await *> sendMessage(message)
          }
        }
    }

  def send(input: ZStream[Any, StatusException, Req]): IO[StatusException, Unit] =
    closed.isDone.flatMap {
      case true  => ZIO.unit
      case false =>
        (input.runForeach(sendMessage) *> closed.isDone.flatMap(done => if (done) ZIO.unit else call.halfClose()))
          .raceFirst(closed.await)
    }
}

private[client] object RequestStreamSender {
  def make[Req, Res](call: ZClientCall[Req, Res], listener: ClientCall.Listener[Res]) =
    for {
      runtime <- ZIO.runtime[Any]
      signal  <- Promise.make[Nothing, Unit]
      ready   <- Ref.make(signal)
      closed  <- Promise.make[Nothing, Unit]
    } yield new RequestStreamSender(call, listener, runtime, ready, closed)
}
