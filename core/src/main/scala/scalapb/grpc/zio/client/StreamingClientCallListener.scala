package scalapb.grpc.zio.client

import zio.Runtime
import zio.Ref

import io.grpc.ClientCall
import io.grpc.{Metadata, Status}
import zio.Queue
import zio.IO
import StreamingCallState._
import zio.stream.ZStream

sealed trait StreamingCallState[+Res]

object StreamingCallState {
  case object Initial extends StreamingCallState[Nothing]

  case class HeadersReceived[Res](headers: Metadata)
      extends StreamingCallState[Res]

  case class Failure[Res](s: String) extends StreamingCallState[Res]
}

class StreamingClientCallListener[Res](
    runtime: Runtime[Any],
    call: ZClientCall[_, Res],
    state: Ref[StreamingCallState[Res]],
    queue: Queue[Either[(Status, Metadata), Res]]
) extends ClientCall.Listener[Res] {

  override def onHeaders(headers: Metadata): Unit =
    runtime.unsafeRun(state.update({
      case Initial            => HeadersReceived(headers)
      case HeadersReceived(_) => Failure("onHeaders already called")
      case f @ Failure(_)     => f
    }).unit)

  override def onMessage(message: Res): Unit =
    runtime.unsafeRun(queue.offer(Right(message)) *> call.request(1))

  override def onClose(status: Status, trailers: Metadata): Unit =
    runtime.unsafeRun(queue.offer(Left((status, trailers))).unit)

  def stream: ZStream[Any, Status, Res] =
    ZStream
      .fromQueue(queue)
      .tap {
        case Left((status, trailers)) =>
          queue.shutdown *> IO.when(!status.isOk)(IO.fail(status))
        case _ => IO.unit
      }
      .collect {
        case Right(v) => v
      }
}

object StreamingClientCallListener {
  def make[Res](call: ZClientCall[_, Res]) =
    for {
      runtime <- zio.ZIO.runtime[Any]
      state <- Ref.make[StreamingCallState[Res]](Initial)
      queue <- Queue.unbounded[Either[(Status, Metadata), Res]]
    } yield new StreamingClientCallListener(runtime, call, state, queue)
}
