package scalapb.zio_grpc.client

import scalapb.zio_grpc.ResponseFrame
import io.grpc.{ClientCall, Metadata, Status, StatusException}
import zio.stream.ZStream
import zio._

class StreamingClientCallListener[Res](
    prefetch: Option[Int],
    runtime: Runtime[Any],
    call: ZClientCall[?, Res],
    queue: Queue[ResponseFrame[Res]],
    buffered: Ref[Int],
    ready: Ref[Promise[Nothing, Unit]],
    closed: Ref[Boolean]
) extends ClientCall.Listener[Res] {
  private val increment = if (prefetch.isDefined) buffered.update(_ + 1) else ZIO.unit
  private val fetchOne  = if (prefetch.isDefined) ZIO.unit else call.request(1)
  private val fetchMore = prefetch match {
    case None    => ZIO.unit
    case Some(n) => buffered.get.flatMap(b => call.request(n - b).when(n > b))
  }

  private def unsafeRun(task: IO[Any, Unit]): Unit =
    Unsafe.unsafe(implicit u => runtime.unsafe.run(task).getOrThrowFiberFailure())

  private def handle(promise: Promise[StatusException, Unit])(
      chunk: Chunk[ResponseFrame[Res]]
  ) = (chunk.lastOption match {
    case Some(ResponseFrame.Trailers(status, trailers)) =>
      val exit = if (status.isOk) Exit.unit else Exit.fail(new StatusException(status, trailers))
      promise.done(exit) *> queue.shutdown
    case _                                              =>
      buffered.update(_ - chunk.size) *> fetchMore
  }).as(chunk)

  override def onReady(): Unit =
    unsafeRun {
      Promise.make[Nothing, Unit].flatMap { next =>
        ready.getAndSet(next).flatMap(_.succeed(()).unit)
      }
    }

  override def onHeaders(headers: Metadata): Unit =
    unsafeRun(queue.offer(ResponseFrame.Headers(headers)) *> increment)

  override def onMessage(message: Res): Unit =
    unsafeRun(queue.offer(ResponseFrame.Message(message)) *> increment *> fetchOne)

  override def onClose(status: Status, trailers: Metadata): Unit =
    unsafeRun(
      closed.set(true) *> ready.get.flatMap(_.succeed(())) *>
        queue.offer(ResponseFrame.Trailers(status, trailers)).unit
    )

  def stream: ZStream[Any, StatusException, ResponseFrame[Res]] =
    ZStream.fromZIO(Promise.make[StatusException, Unit]).flatMap { promise =>
      ZStream
        .fromQueue(queue, prefetch.getOrElse(ZStream.DefaultChunkSize))
        .mapChunksZIO(handle(promise))
        .concat(ZStream.execute(promise.await))
    }

  def onReadySignal: UIO[Promise[Nothing, Unit]] = ready.get

  def isClosed: UIO[Boolean] = closed.get
}

object StreamingClientCallListener {
  def make[Res](call: ZClientCall[?, Res], prefetch: Option[Int]): UIO[StreamingClientCallListener[Res]] = for {
    runtime  <- ZIO.runtime[Any]
    queue    <- Queue.unbounded[ResponseFrame[Res]]
    buffered <- Ref.make(0)
    ready0   <- Promise.make[Nothing, Unit]
    ready    <- Ref.make[Promise[Nothing, Unit]](ready0)
    closed   <- Ref.make(false)
  } yield new StreamingClientCallListener(prefetch, runtime, call, queue, buffered, ready, closed)
}
