package scalapb.zio_grpc.client

import scalapb.zio_grpc.ResponseFrame
import io.grpc.{ClientCall, Metadata, Status, StatusException}
import zio.stream.ZStream
import zio._

class StreamingClientCallListener[Res](
    runtime: Runtime[Any],
    call: ZClientCall[_, Res],
    queue: Queue[ResponseFrame[Res]]
) extends ClientCall.Listener[Res] {

  override def onHeaders(headers: Metadata): Unit =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          queue
            .offer(ResponseFrame.Headers(headers))
            .unit
        )
        .getOrThrowFiberFailure()
    }

  override def onMessage(message: Res): Unit =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe.run(queue.offer(ResponseFrame.Message(message)) *> call.request(1)).getOrThrowFiberFailure()
    }

  override def onClose(status: Status, trailers: Metadata): Unit =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe.run(queue.offer(ResponseFrame.Trailers(status, trailers)).unit).getOrThrowFiberFailure()
    }

  def stream: ZStream[Any, StatusException, ResponseFrame[Res]] =
    ZStream
      .fromQueue(queue)
      .tap {
        case ResponseFrame.Trailers(status, trailers) =>
          queue.shutdown *> ZIO.when(!status.isOk)(ZIO.fail(new StatusException(status, trailers)))
        case _                                        => ZIO.unit
      }
}

object StreamingClientCallListener {
  def make[Res](
      call: ZClientCall[_, Res]
  ): UIO[StreamingClientCallListener[Res]] =
    for {
      runtime <- zio.ZIO.runtime[Any]
      queue   <- Queue.unbounded[ResponseFrame[Res]]
    } yield new StreamingClientCallListener(runtime, call, queue)
}
