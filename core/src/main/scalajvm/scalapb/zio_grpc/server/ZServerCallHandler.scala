package scalapb.zio_grpc.server

import zio._
import io.grpc.ServerCall.Listener
import io.grpc.Status
import zio.stream.Stream
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import zio.stream.ZStream
import scalapb.zio_grpc.RequestContext
import io.grpc.Metadata
import scalapb.zio_grpc.SafeMetadata
import zio.stm.TSemaphore

class ZServerCallHandler[Req, Res](
    runtime: Runtime[Any],
    mkListener: (ZServerCall[Res], RequestContext) => UIO[Listener[Req]]
) extends ServerCallHandler[Req, Res] {
  def startCall(
      call: ServerCall[Req, Res],
      headers: Metadata
  ): Listener[Req] = {
    val runner = for {
      responseMetadata <- SafeMetadata.make
      canSend          <- TSemaphore.make(1).commit
      zioCall           = new ZServerCall(call, canSend)
      md               <- SafeMetadata.fromMetadata(headers)
      listener         <- mkListener(zioCall, RequestContext.fromServerCall(md, responseMetadata, call))
    } yield listener

    Unsafe.unsafe { implicit u =>
      runtime.unsafe.run(runner).getOrThrowFiberFailure()
    }
  }
}

object ZServerCallHandler {
  private[zio_grpc] val queueSizeProp = "zio-grpc.backpressure-queue-size"

  val backpressureQueueSize: IO[Status, Int] =
    ZIO
      .attempt(sys.props.get(queueSizeProp).map(_.toInt).getOrElse(16))
      .refineToOrDie[NumberFormatException]
      .catchAll { t =>
        ZIO.fail(Status.INTERNAL.withDescription(s"$queueSizeProp: ${t.getMessage}"))
      }

  def unaryInput[Req, Res](
      runtime: Runtime[Any],
      impl: (Req, RequestContext, ZServerCall[Res]) => ZIO[Any, Status, Unit]
  ): ServerCallHandler[Req, Res] =
    new ZServerCallHandler(runtime, ListenerDriver.makeUnaryInputListener(impl, runtime))

  def streamingInput[Req, Res](
      runtime: Runtime[Any],
      impl: (
          Stream[Status, Req],
          RequestContext,
          ZServerCall[Res]
      ) => ZIO[Any, Status, Unit]
  ): ServerCallHandler[Req, Res] =
    new ZServerCallHandler(
      runtime,
      ListenerDriver.makeStreamingInputListener(impl)
    )

  def unaryCallHandler[Req, Res](
      runtime: Runtime[Any],
      impl: (Req, RequestContext) => ZIO[Any, Status, Res]
  ): ServerCallHandler[Req, Res] =
    unaryInput[Req, Res](
      runtime,
      (req, requestContext, call) => impl(req, requestContext).flatMap[Any, Status, Unit](call.sendMessage)
    )

  def serverStreamingCallHandler[Req, Res](
      runtime: Runtime[Any],
      impl: (Req, RequestContext) => ZStream[Any, Status, Res]
  ): ServerCallHandler[Req, Res] =
    unaryInput[Req, Res](
      runtime,
      (req: Req, requestContext: RequestContext, call: ZServerCall[Res]) =>
        serverStreamingWithBackpressure(call, impl(req, requestContext))
    )

  def clientStreamingCallHandler[Req, Res](
      runtime: Runtime[Any],
      impl: (Stream[Status, Req], RequestContext) => ZIO[Any, Status, Res]
  ): ServerCallHandler[Req, Res] =
    streamingInput[Req, Res](
      runtime,
      (req, requestContext, call) => impl(req, requestContext).flatMap[Any, Status, Unit](call.sendMessage)
    )

  def bidiCallHandler[Req, Res](
      runtime: Runtime[Any],
      impl: (Stream[Status, Req], RequestContext) => ZStream[Any, Status, Res]
  ): ServerCallHandler[Req, Res] =
    streamingInput[Req, Res](
      runtime,
      (req, requestContext, call) => serverStreamingWithBackpressure(call, impl(req, requestContext))
    )

  def serverStreamingWithBackpressure[Res](
      call: ZServerCall[Res],
      stream: ZStream[Any, Status, Res]
  ): ZIO[Any, Status, Unit] = {
    def innerLoop(queue: Dequeue[Exit[Option[Status], Res]]): ZIO[Any, Status, Boolean] =
      queue.take
        .flatMap {
          case Exit.Success(res)   => call.sendMessage(res).as(true)
          case Exit.Failure(cause) =>
            cause.failureOrCause match {
              case Left(Some(status)) => ZIO.fail(status)
              case Left(None)         => ZIO.succeed(false)
              case Right(cause)       => ZIO.failCause(cause)
            }
        }
        .repeatWhileZIO(res => call.isReady.map(_ && res))

    def outerLoop(queue: Dequeue[Exit[Option[Status], Res]]): ZIO[Any, Status, Boolean] =
      (call.awaitReady *> innerLoop(queue))
        .repeatWhile(identity)

    for {
      queueSize <- backpressureQueueSize
      _         <- ZIO.scoped[Any](
                     stream
                       .toQueueOfElements(queueSize)
                       .flatMap(outerLoop)
                   )
    } yield ()
  }
}
