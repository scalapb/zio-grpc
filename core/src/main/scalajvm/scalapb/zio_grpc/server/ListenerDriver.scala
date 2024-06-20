package scalapb.zio_grpc.server

import io.grpc.ServerCall.Listener
import io.grpc.{Metadata, Status, StatusException}
import zio._
import zio.stream.{Stream, ZStream}
import scalapb.zio_grpc.RequestContext

object ListenerDriver {
  def exitToStatus(ex: Exit[StatusException, Unit]): Status =
    ex.foldExit(
      failed = { cause =>
        if (cause.isInterruptedOnly) Status.CANCELLED
        else cause.failureOption.map(_.getStatus).getOrElse(Status.INTERNAL)
      },
      completed = _ => Status.OK
    )

  def unaryInputListener[Req](
      runtime: Runtime[Any],
      call: ZServerCall[_],
      completed: Promise[StatusException, Unit],
      request: Promise[Nothing, Req],
      requestContext: RequestContext,
      writeResponse: Req => ZIO[Any, StatusException, Unit]
  ): ZIO[Any, Nothing, Listener[Req]] =
    (
      call.request(2) *>
        completed.await *>
        call.sendHeaders(new Metadata) *>
        request.await flatMap writeResponse
    ).tapError(statusException => requestContext.responseMetadata.wrap(_.merge(statusException.getTrailers)).ignore)
      .onExit(ex => call.close(ListenerDriver.exitToStatus(ex), requestContext.responseMetadata.metadata).ignore)
      .ignore
      // Why forkDaemon? we need the driver to keep runnning in the background after we return a listener
      // back to grpc-java. If it was just fork, the call to unsafeRun would not return control, so grpc-java
      // won't have a listener to call on.  The driver awaits on the calls to the listener to pass to the user's
      // service.
      .forkDaemon
      .map(fiber =>
        new Listener[Req] {
          override def onCancel(): Unit =
            Unsafe.unsafe { implicit u =>
              runtime.unsafe.run(fiber.interruptFork.unit).getOrThrowFiberFailure()
            }

          override def onHalfClose(): Unit =
            Unsafe.unsafe { implicit u =>
              runtime.unsafe.run(completed.completeWith(ZIO.unit).unit).getOrThrowFiberFailure()
            }

          override def onMessage(message: Req): Unit =
            Unsafe.unsafe { implicit u =>
              runtime.unsafe
                .run {
                  request.succeed(message).flatMap {
                    case false =>
                      completed
                        .fail(Status.INTERNAL.withDescription("Too many requests").asException())
                        .unit
                    case true  =>
                      ZIO.unit
                  }
                }
                .getOrThrowFiberFailure()
            }

          override def onReady(): Unit =
            Unsafe.unsafe { implicit u =>
              runtime.unsafe.run(call.setReady())
              ()
            }
        }
      )

  /** Creates a [[Listener]] for a request with a unary input.
    *
    * writeResponse: given a request, returns a effects that computes the response and writes it through the given
    * ZServerCall.
    */
  def makeUnaryInputListener[Req, Res](
      writeResponse: (
          Req,
          RequestContext,
          ZServerCall[Res]
      ) => ZIO[Any, StatusException, Unit],
      runtime: Runtime[Any]
  )(
      zioCall: ZServerCall[Res],
      requestContext: RequestContext
  ): ZIO[Any, Nothing, Listener[Req]] =
    for {
      completed <- Promise.make[StatusException, Unit]
      request   <- Promise.make[Nothing, Req]
      listener  <- unaryInputListener(
                     runtime,
                     zioCall,
                     completed,
                     request,
                     requestContext,
                     writeResponse(_, requestContext, zioCall)
                   )
    } yield listener

  def streamingInputListener[Req, Res](
      runtime: Runtime[Any],
      call: ZServerCall[Res],
      queue: Queue[Option[Req]],
      requestContext: RequestContext,
      writeResponse: Stream[StatusException, Req] => ZIO[Any, StatusException, Unit]
  ): ZIO[Any, Nothing, Listener[Req]] = {
    val requestStream = ZStream
      .fromQueue(queue)
      .collectWhileSome

    (call.request(1) *>
      call.sendHeaders(new Metadata) *>
      writeResponse(requestStream))
      .onExit(ex => call.close(ListenerDriver.exitToStatus(ex), requestContext.responseMetadata.metadata).ignore)
      .ignore
      .forkDaemon
      .map(fiber =>
        new Listener[Req] {
          override def onCancel(): Unit =
            Unsafe.unsafe { implicit u =>
              runtime.unsafe.run(fiber.interruptFork.unit).getOrThrowFiberFailure()
            }

          override def onHalfClose(): Unit =
            Unsafe.unsafe { implicit u =>
              runtime.unsafe.run(queue.offer(None).unit).getOrThrowFiberFailure()
            }

          override def onMessage(message: Req): Unit =
            Unsafe.unsafe { implicit u =>
              runtime.unsafe
                .run(
                  call.request(1) *> queue.offer(Some(message)).unit
                )
                .getOrThrowFiberFailure()
            }

          override def onReady(): Unit =
            Unsafe.unsafe { implicit u =>
              runtime.unsafe.run(call.setReady())
              ()
            }
        }
      )
  }

  /** Creates a [[Listener]] for a request with a streaming input.
    *
    * writeResponse: given a request, returns a effects that computes the response and writes it through the given
    * ZServerCall.
    */
  def makeStreamingInputListener[Req, Res](
      writeResponse: (
          Stream[StatusException, Req],
          RequestContext,
          ZServerCall[Res]
      ) => ZIO[Any, StatusException, Unit]
  )(
      zioCall: ZServerCall[Res],
      requestContext: RequestContext
  ): ZIO[Any, Nothing, Listener[Req]] =
    for {
      runtime  <- ZIO.runtime[Any]
      queue    <- Queue.unbounded[Option[Req]]
      listener <- streamingInputListener[Req, Res](
                    runtime,
                    zioCall,
                    queue,
                    requestContext,
                    writeResponse(_, requestContext, zioCall)
                  )
    } yield listener
}
