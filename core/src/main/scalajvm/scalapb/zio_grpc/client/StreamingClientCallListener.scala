package scalapb.zio_grpc.client

import scalapb.zio_grpc.ResponseFrame
import io.grpc.{ClientCall, Metadata, Status, StatusException}
import zio.stream.ZStream
import zio._

class StreamingClientCallListener[Res](
    prefetch: Option[Int],
    runtime: Runtime[Any],
    call: ZClientCall[?, Res],
    queue: Queue[ResponseFrame[Res]]
) extends ClientCall.Listener[Res] {
  private val fetchOne =
    ZIO.whenDiscard(prefetch.isEmpty)(call.request(1))

  private def fetchMore(n: Int) =
    ZIO.whenDiscard(prefetch.isDefined)(call.request(n))

  private def unsafeRun(task: IO[Any, Unit]): Unit =
    Unsafe.unsafe(implicit u => runtime.unsafe.run(task).getOrThrowFiberFailure())

  private def handle(promise: Promise[StatusException, Unit])(chunk: Chunk[ResponseFrame[Res]]) =
    ZIO.unlessDiscard(chunk.isEmpty)(chunk.last match {
      case ResponseFrame.Trailers(status, trailers) =>
        val exit =
          if (status.isOk) Exit.unit
          else Exit.fail(new StatusException(status, trailers))
        promise.done(exit) *> queue.shutdown
      case _                                        =>
        fetchMore(chunk.size)
    })

  override def onHeaders(headers: Metadata): Unit =
    unsafeRun(queue.offer(ResponseFrame.Headers(headers)).unit)

  override def onMessage(message: Res): Unit =
    unsafeRun(queue.offer(ResponseFrame.Message(message)) *> fetchOne)

  override def onClose(status: Status, trailers: Metadata): Unit =
    unsafeRun(queue.offer(ResponseFrame.Trailers(status, trailers)).unit)

  def stream: ZStream[Any, StatusException, ResponseFrame[Res]] =
    ZStream.fromZIO(Promise.make[StatusException, Unit]).flatMap { promise =>
      ZStream
        .fromQueue(queue, prefetch.getOrElse(ZStream.DefaultChunkSize))
        .tapChunks(handle(promise))
        .concat(ZStream.execute(promise.await))
    }
}

object StreamingClientCallListener {
  def make[Res](call: ZClientCall[?, Res], prefetch: Option[Int]): UIO[StreamingClientCallListener[Res]] = for {
    runtime <- ZIO.runtime[Any]
    queue   <- Queue.unbounded[ResponseFrame[Res]]
  } yield new StreamingClientCallListener(prefetch, runtime, call, queue)
}
