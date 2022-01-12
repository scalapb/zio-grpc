package scalapb.zio_grpc.client

import zio.Runtime
import zio.Ref

import io.grpc.ClientCall
import io.grpc.{Metadata, Status}
import zio.Queue
import zio.IO
import StreamingCallState._
import zio.stream.ZStream
import zio.URIO

sealed trait StreamingCallState[+Res]

object StreamingCallState {
  case object Initial extends StreamingCallState[Nothing]

  case class HeadersReceived[Res](headers: Metadata) extends StreamingCallState[Res]

  case class Failure[Res](s: String) extends StreamingCallState[Res]
}

class StreamingClientCallListener[R, Res](
    runtime: Runtime[R],
    call: ZClientCall[R, _, Res],
    state: Ref[StreamingCallState[Res]],
    queue: Queue[Either[(Status, Metadata), Res]]
) extends ClientCall.Listener[Res] {

  override def onHeaders(headers: Metadata): Unit =
    runtime.unsafeRun(
      state.update {
        case Initial            => HeadersReceived(headers)
        case HeadersReceived(_) => Failure("onHeaders already called")
        case f @ Failure(_)     => f
      }.unit
    )

  override def onMessage(message: Res): Unit =
    runtime.unsafeRun(queue.offer(Right(message)) *> call.request(1))

  override def onClose(status: Status, trailers: Metadata): Unit =
    runtime.unsafeRun(queue.offer(Left((status, trailers))).unit)

  def stream: ZStream[Any, Status, Res] =
    ZStream
      .fromQueue(queue)
      .tap {
        case Left((status, trailers @ _)) =>
          queue.shutdown *> IO.when(!status.isOk)(IO.fail(status))
        case _                            => IO.unit
      }
      .collect { case Right(v) =>
        v
      }
}

object StreamingClientCallListener {
  def make[R, Res](
      call: ZClientCall[R, _, Res]
  ): URIO[R, StreamingClientCallListener[R, Res]] =
    for {
      runtime <- zio.ZIO.runtime[R]
      state   <- Ref.make[StreamingCallState[Res]](Initial)
      queue   <- Queue.unbounded[Either[(Status, Metadata), Res]]
    } yield new StreamingClientCallListener(runtime, call, state, queue)
}
