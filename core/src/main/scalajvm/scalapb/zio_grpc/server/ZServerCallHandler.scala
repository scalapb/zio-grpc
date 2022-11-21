package scalapb.zio_grpc.server

import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCall.Listener
import io.grpc.ServerCallHandler
import io.grpc.Status
import scalapb.zio_grpc.RequestContext
import scalapb.zio_grpc.SafeMetadata
import zio._
import zio.stm.TSemaphore
import zio.stream.Stream
import zio.stream.ZStream

class ZServerCallHandler[R, Req, Res](
    runtime: Runtime[R],
    mkDriver: (ZServerCall[Res], TSemaphore, RequestContext) => URIO[R, CallDriver[R, Req]]
) extends ServerCallHandler[Req, Res] {
  def startCall(
      call: ServerCall[Req, Res],
      headers: Metadata
  ): Listener[Req] = {
    val zioCall = new ZServerCall(call)
    val runner  = for {
      rmd     <- SafeMetadata.make
      md      <- SafeMetadata.fromMetadata(headers)
      canSend <- TSemaphore.makeCommit(1)
      driver  <- mkDriver(zioCall, canSend, RequestContext.fromServerCall(md, rmd, call))
      // Why forkDaemon? we need the driver to keep runnning in the background after we return a listener
      // back to grpc-java. If it was just fork, the call to unsafeRun would not return control, so grpc-java
      // won't have a listener to call on.  The driver awaits on the calls to the listener to pass to the user's
      // service.
      _       <- driver.run.forkDaemon
    } yield driver.listener

    Unsafe.unsafe { implicit u =>
      runtime.unsafe.run(runner).getOrThrowFiberFailure()
    }
  }
}

object ZServerCallHandler {
  def unaryInput[R, Req, Res](
      runtime: Runtime[R],
      impl: (Req, RequestContext, ZServerCall[Res], TSemaphore) => ZIO[R, Status, Unit]
  ): ServerCallHandler[Req, Res] =
    new ZServerCallHandler(runtime, CallDriver.makeUnaryInputCallDriver(impl))

  def streamingInput[R, Req, Res](
      runtime: Runtime[R],
      impl: (
          Stream[Status, Req],
          RequestContext,
          ZServerCall[Res],
          TSemaphore
      ) => ZIO[R, Status, Unit]
  ): ServerCallHandler[Req, Res] =
    new ZServerCallHandler(
      runtime,
      CallDriver.makeStreamingInputCallDriver(impl)
    )

  def unaryCallHandler[Req, Res](
      runtime: Runtime[Any],
      impl: Req => ZIO[RequestContext, Status, Res]
  ): ServerCallHandler[Req, Res] =
    unaryInput[Any, Req, Res](
      runtime,
      (req, requestContext, call, _) =>
        impl(req).provideEnvironment(ZEnvironment(requestContext)).flatMap(call.sendMessage)
    )

  def serverStreamingCallHandler[Req, Res](
      runtime: Runtime[Any],
      impl: Req => ZStream[RequestContext, Status, Res]
  ): ServerCallHandler[Req, Res] =
    unaryInput[Any, Req, Res](
      runtime,
      (req: Req, requestContext: RequestContext, call: ZServerCall[Res], canSend: TSemaphore) =>
        serverStreamingWithBackpressure(
          call,
          canSend,
          requestContext,
          impl(req).provideEnvironment(ZEnvironment(requestContext))
        )
    )

  def clientStreamingCallHandler[Req, Res](
      runtime: Runtime[Any],
      impl: Stream[Status, Req] => ZIO[RequestContext, Status, Res]
  ): ServerCallHandler[Req, Res] =
    streamingInput[Any, Req, Res](
      runtime,
      (req, requestContext, call, _) =>
        impl(req).provideEnvironment(ZEnvironment(requestContext)).flatMap(call.sendMessage)
    )

  def bidiCallHandler[Req, Res](
      runtime: Runtime[Any],
      impl: Stream[Status, Req] => ZStream[RequestContext, Status, Res]
  ): ServerCallHandler[Req, Res] =
    streamingInput(
      runtime,
      (req, requestContext, call, canSend) =>
        serverStreamingWithBackpressure(
          call,
          canSend,
          requestContext,
          impl(req).provideEnvironment(ZEnvironment(requestContext))
        )
    )

  // Dispatches items from the stream to the client, and awaits until the client is ready to receive
  // more. Elements of type `Res` are pulled from the stream one by one and sent to the client until
  // the underlying call signals it is not ready to receive more. At that point, the fiber awaits until
  // the listener receiving an `onReady` signal provides an execution permit; coordination is done via
  // a semaphore.
  private[scalapb] def serverStreamingWithBackpressure[Res](
      call: ZServerCall[Res],
      canSend: TSemaphore,
      ctx: RequestContext,
      stream: ZStream[Any, Status, Res]
  ): ZIO[Any, Status, Unit] = {
    // Take from the queue until either we reach the end of it or
    // the client is not ready to receive more messages.
    def innerLoop(queue: Dequeue[Exit[Option[Status], Res]]) =
      queue.take
        .flatMap {
          case Exit.Success(res)   =>
            call.sendMessage(res).as(true)
          case Exit.Failure(cause) =>
            cause.failureOrCause match {
              case Left(Some(status)) =>
                ZIO.fail(status)
              case Left(None)         =>
                ZIO.succeed(false)
              case Right(cause)       =>
                ZIO.failCause(cause)
            }
        }
        .repeatWhile(_ && call.isReady)

    // Acquire a permit then read as much as possible from the queue, stop
    // when the queue has offered its last element (i.e. Exit.fail(None))
    def outerLoop(queue: Dequeue[Exit[Option[Status], Res]]) =
      (canSend.acquire.commit *> innerLoop(queue)).repeatWhile(identity)

    ZIO
      .scoped[Any](
        stream
          .toQueueOfElements(16)
          // ^ Would need to benchmark the optimal size for this queue,
          // but 16 seems like a reasonable default esp. as right now that
          // buffer is unbounded.
          .flatMap(outerLoop)
      )
      .unit
  }

}
