package scalapb.zio_grpc.server

import zio._
import io.grpc.ServerCall.Listener
import io.grpc.Status
import io.grpc.Metadata
import zio.stream.{Stream, ZStream}

trait CommonListener[R, Req, InputType] extends Listener[Req] {
  def serveInner(
      sendResponse: InputType => ZIO[R, Status, Unit],
      metadata: Metadata
  ): URIO[R, Unit]

  def serveUnary[Res](
      call: ZServerCall[Res]
  )(
      impl: InputType => ZIO[R, Status, Res],
      metadata: Metadata
  ) = serveInner(impl(_) >>= call.sendMessage, metadata)

  def serveStreaming[Res](call: ZServerCall[Res])(
      impl: InputType => ZStream[R, Status, Res],
      metadata: Metadata
  ) = serveInner(impl(_).foreach(call.sendMessage), metadata)
}

object CommonListener {
  def exitHandler(call: ZServerCall[_]) = (ex: Exit[Status, Unit]) => {
    (ex.untraced match {
      case Exit.Success(_) =>
        call.close(Status.OK, new Metadata)
      case Exit.Failure(c) if c.interrupted =>
        call.close(
          Status.CANCELLED,
          new Metadata
        )
      case Exit.Failure(Cause.Fail(e)) =>
        call.close(
          e,
          new Metadata
        )
      case Exit.Failure(c) =>
        call.close(Status.INTERNAL, new Metadata)
    }).ignore
  }
}

class UnaryCallListener[R, Req](
    runtime: Runtime[R],
    call: ZServerCall[_],
    cancelled: Promise[Nothing, Unit],
    completed: Promise[Status, Unit],
    request: Promise[Nothing, Req]
) extends Listener[Req]
    with CommonListener[R, Req, Req] {
  override def onCancel(): Unit = runtime.unsafeRun(cancelled.succeed(()).unit)

  override def onHalfClose(): Unit =
    runtime.unsafeRun(completed.completeWith(ZIO.unit).unit)

  override def onMessage(message: Req): Unit = {
    runtime.unsafeRun {
      request.succeed(message).flatMap {
        case false =>
          completed
            .fail(
              Status.INTERNAL
                .withDescription("Too many requests")
            )
            .unit
        case true =>
          ZIO.unit
      }
    }
  }

  override def serveInner(
      sendResponse: Req => ZIO[R, Status, Unit],
      metadata: Metadata
  ) =
    (
      call.request(2) *>
        completed.await *>
        call.sendHeaders(new Metadata) *>
        request.await >>= sendResponse
    ).onExit(CommonListener.exitHandler(call))
      .ignore
      .race(cancelled.await)
}

object UnaryCallListener {
  def make[R, Req](
      zioCall: ZServerCall[_]
  ): ZIO[R, Nothing, UnaryCallListener[R, Req]] = {
    for {
      runtime <- ZIO.runtime[R]
      cancelled <- Promise.make[Nothing, Unit]
      completed <- Promise.make[Status, Unit]
      request <- Promise.make[Nothing, Req]
    } yield new UnaryCallListener(
      runtime,
      zioCall,
      cancelled,
      completed,
      request
    )
  }
}

class StreamingCallListener[R, Req](
    runtime: Runtime[R],
    call: ZServerCall[_],
    cancelled: Promise[Nothing, Unit],
    queue: Queue[Either[Option[Status], Req]]
) extends Listener[Req]
    with CommonListener[R, Req, Stream[Status, Req]] {
  override def onCancel(): Unit = runtime.unsafeRun(cancelled.succeed(()).unit)

  override def onHalfClose(): Unit = {
    runtime.unsafeRun(queue.offer(Left(None)).unit)
  }

  override def onMessage(message: Req): Unit = {
    runtime.unsafeRun(
      call.request(1) *> queue.offer(Right(message)).unit
    )
  }

  override def serveInner(
      sendResponse: Stream[Status, Req] => ZIO[R, Status, Unit],
      metadata: Metadata
  ) = {
    val requestStream = ZStream
      .fromQueue(queue)
      .tap {
        case Left(None) => queue.shutdown
        case Left(Some(status)) =>
          queue.shutdown *> ZIO.fail(status)
        case _ => ZIO.unit
      }
      .collect {
        case Right(v) => v
      }

    (call.request(1) *>
      call.sendHeaders(new Metadata) *>
      sendResponse(requestStream))
      .onExit(CommonListener.exitHandler(call))
      .ignore
      .race(cancelled.await)
  }
}

object StreamingCallListener {
  def make[R, Req](
      zioCall: ZServerCall[_]
  ): ZIO[R, Nothing, StreamingCallListener[R, Req]] = {
    for {
      runtime <- ZIO.runtime[R]
      cancelled <- Promise.make[Nothing, Unit]
      queue <- Queue.unbounded[Either[Option[Status], Req]]
    } yield new StreamingCallListener[R, Req](
      runtime,
      zioCall,
      cancelled,
      queue
    )
  }
}
