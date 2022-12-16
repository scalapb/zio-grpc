package scalapb.zio_grpc.client

import io.grpc.{ClientCall, Metadata, Status}
import scalapb.zio_grpc.ResponseFrame
import zio.{IO, Queue, Runtime, URIO}
import zio.stream.ZStream

class StreamingClientCallListener[R, Res](
    runtime: Runtime[R],
    call: ZClientCall[R, _, Res],
    queue: Queue[ResponseFrame[Res]]
) extends ClientCall.Listener[Res] {

  override def onHeaders(headers: Metadata): Unit = {
    val _ = runtime.unsafeRun(queue.offer(ResponseFrame.Headers(headers)))
  }

  override def onMessage(message: Res): Unit =
    runtime.unsafeRun(queue.offer(ResponseFrame.Message(message)) *> call.request(1))

  override def onClose(status: Status, trailers: Metadata): Unit = {
    val _ = runtime.unsafeRun(queue.offer(ResponseFrame.Trailers(status, trailers)))
  }

  def stream: ZStream[Any, Status, ResponseFrame[Res]] =
    ZStream
      .fromQueue(queue)
      .tap {
        case ResponseFrame.Trailers(status, trailers) if !status.isOk =>
          queue.shutdown *> IO.fail(status.withCause(status.asException(trailers)))
        case ResponseFrame.Trailers(_, _)                      => queue.shutdown
        case _                                                 => IO.unit
      }
}

object StreamingClientCallListener {
  def make[R, Res](
      call: ZClientCall[R, _, Res]
  ): URIO[R, StreamingClientCallListener[R, Res]] =
    for {
      runtime <- zio.ZIO.runtime[R]
      queue   <- Queue.unbounded[ResponseFrame[Res]]
    } yield new StreamingClientCallListener(runtime, call, queue)
}
