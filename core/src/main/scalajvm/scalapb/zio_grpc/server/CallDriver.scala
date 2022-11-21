package scalapb.zio_grpc.server

import io.grpc.ServerCall.Listener
import io.grpc.Status
import zio._
import zio.stream.{Stream, ZStream}
import scalapb.zio_grpc.RequestContext
import io.grpc.Metadata
import zio.stm.TSemaphore

/** Represents a running request to be served by [[ZServerCallHandler]]
  *
  * The listener is returned to grpc-java to feed input into the request.
  *
  * The `run` represents an effect of running the request: it reads the input provided to the listener, and writes the
  * response to an output channel. It handles interrupts by sending a cancel event through the channel.
  */
case class CallDriver[R, Req](
    listener: Listener[Req],
    run: ZIO[R, Status, Unit]
)

object CallDriver {
  def exitToStatus(ex: Exit[Status, Unit]): Status =
    ex.foldExit(
      failed = { cause =>
        if (cause.isInterruptedOnly) Status.CANCELLED
        else cause.failureOption.getOrElse(Status.INTERNAL)
      },
      completed = _ => Status.OK
    )

  def unaryInputCallDriver[R, Req](
      runtime: Runtime[R],
      call: ZServerCall[_],
      canSend: TSemaphore,
      cancelled: Promise[Nothing, Unit],
      completed: Promise[Status, Unit],
      request: Promise[Nothing, Req],
      requestContext: RequestContext,
      writeResponse: Req => ZIO[R, Status, Unit]
  ): CallDriver[R, Req] =
    CallDriver(
      listener = new Listener[Req] {
        override def onCancel(): Unit =
          Unsafe.unsafe { implicit u =>
            val _ = runtime.unsafe.run(cancelled.succeed(())).getOrThrowFiberFailure()
          }

        override def onHalfClose(): Unit =
          Unsafe.unsafe { implicit u =>
            val _ = runtime.unsafe.run(completed.succeed(())).getOrThrowFiberFailure()
          }

        override def onMessage(message: Req): Unit =
          Unsafe.unsafe { implicit u =>
            val _ = runtime.unsafe
              .run {
                request.succeed(message).flatMap {
                  case false =>
                    completed
                      .fail(Status.INTERNAL.withDescription("Too many requests"))
                  case true  =>
                    ZIO.unit
                }
              }
              .getOrThrowFiberFailure()
          }

        override def onReady(): Unit =
          Unsafe.unsafe { implicit u =>
            val _ = runtime.unsafe
              .run(
                canSend.release.commit
              )
              .getOrThrowFiberFailure()
          }
      },
      run = (
        call.request(2) *>
          completed.await *>
          call.sendHeaders(new Metadata) *>
          request.await flatMap writeResponse
      ).onExit(ex => call.close(CallDriver.exitToStatus(ex), requestContext.responseMetadata.metadata).ignore)
        .ignore
        .race(cancelled.await)
    )

  /** Creates a [[CallDriver]] for a request with a unary input.
    *
    * writeResponse: given a request, returns a effects that computes the response and writes it through the given
    * ZServerCall.
    */
  def makeUnaryInputCallDriver[R, Req, Res](
      writeResponse: (
          Req,
          RequestContext,
          ZServerCall[Res],
          TSemaphore
      ) => ZIO[R, Status, Unit]
  )(
      zioCall: ZServerCall[Res],
      canSend: TSemaphore,
      requestContext: RequestContext
  ): ZIO[R, Nothing, CallDriver[R, Req]] =
    for {
      runtime   <- ZIO.runtime[R]
      cancelled <- Promise.make[Nothing, Unit]
      completed <- Promise.make[Status, Unit]
      request   <- Promise.make[Nothing, Req]
    } yield unaryInputCallDriver(
      runtime,
      zioCall,
      canSend,
      cancelled,
      completed,
      request,
      requestContext,
      writeResponse(_, requestContext, zioCall, canSend)
    )

  def streamingInputCallDriver[R, Req, Res](
      runtime: Runtime[R],
      call: ZServerCall[Res],
      canSend: TSemaphore,
      cancelled: Promise[Nothing, Unit],
      queue: Queue[Option[Req]],
      requestContext: RequestContext,
      writeResponse: Stream[Status, Req] => ZIO[R, Status, Unit]
  ): CallDriver[R, Req] =
    CallDriver(
      listener = new Listener[Req] {
        override def onCancel(): Unit =
          Unsafe.unsafe { implicit u =>
            val _ = runtime.unsafe.run(cancelled.succeed(())).getOrThrowFiberFailure()
          }

        override def onHalfClose(): Unit =
          Unsafe.unsafe { implicit u =>
            val _ = runtime.unsafe.run(queue.offer(None)).getOrThrowFiberFailure()
          }

        override def onMessage(message: Req): Unit =
          Unsafe.unsafe { implicit u =>
            val _ = runtime.unsafe
              .run(
                call.request(1) *> queue.offer(Some(message))
              )
              .getOrThrowFiberFailure()
          }

        override def onReady(): Unit =
          Unsafe.unsafe { implicit u =>
            val _ = runtime.unsafe
              .run(
                canSend.release.commit
              )
              .getOrThrowFiberFailure()
          }
      },
      run = {
        val requestStream = ZStream
          .fromQueue(queue)
          .collectWhileSome

        (call.request(1) *>
          call.sendHeaders(new Metadata) *>
          writeResponse(requestStream))
          .onExit(ex => call.close(CallDriver.exitToStatus(ex), requestContext.responseMetadata.metadata).ignore)
          .ignore
          .race(cancelled.await)
      }
    )

  /** Creates a [[CallDriver]] for a request with a streaming input.
    *
    * writeResponse: given a request, returns a effects that computes the response and writes it through the given
    * ZServerCall.
    */
  def makeStreamingInputCallDriver[R, Req, Res](
      writeResponse: (
          Stream[Status, Req],
          RequestContext,
          ZServerCall[Res],
          TSemaphore
      ) => ZIO[R, Status, Unit]
  )(
      zioCall: ZServerCall[Res],
      canSend: TSemaphore,
      requestContext: RequestContext
  ): ZIO[R, Nothing, CallDriver[R, Req]] =
    for {
      runtime   <- ZIO.runtime[R]
      cancelled <- Promise.make[Nothing, Unit]
      queue     <- Queue.unbounded[Option[Req]]
    } yield streamingInputCallDriver[R, Req, Res](
      runtime,
      zioCall,
      canSend,
      cancelled,
      queue,
      requestContext,
      writeResponse(_, requestContext, zioCall, canSend)
    )
}
