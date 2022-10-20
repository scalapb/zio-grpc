package scalapb.zio_grpc.client

import zio.{Queue, Runtime, URIO, ZIO}
import scalapb.zio_grpc.ResponseFrame
import io.grpc.ClientCall
import io.grpc.{Metadata, Status}
import zio.stream.ZStream
import zio._

class StreamingClientCallListener[R, Res](
    runtime: Runtime[R],
    call: ZClientCall[R, _, Res],
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

  def stream: ZStream[Any, Status, ResponseFrame[Res]] =
    ZStream
      .fromQueue(queue)
      .tap {
        case ResponseFrame.Trailers(status, _) => queue.shutdown *> ZIO.when(!status.isOk)(ZIO.fail(status))
        case _                                 => ZIO.unit
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
