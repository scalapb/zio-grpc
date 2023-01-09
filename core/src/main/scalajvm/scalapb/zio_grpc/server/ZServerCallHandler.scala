package scalapb.zio_grpc.server

import zio._
import io.grpc.ServerCall.Listener
import io.grpc.{Status, StatusException}
import zio.stream.Stream
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import zio.stream.ZStream
import scalapb.zio_grpc.RequestContext
import io.grpc.Metadata
import scalapb.zio_grpc.SafeMetadata
import zio.stm.TSemaphore
import zio.Exit.Failure
import zio.Exit.Success
import scala.annotation.tailrec

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

  val backpressureQueueSize: IO[StatusException, Int] =
    ZIO
      .attempt(sys.props.get(queueSizeProp).map(_.toInt).getOrElse(16))
      .refineToOrDie[NumberFormatException]
      .catchAll { t =>
        ZIO.fail(Status.INTERNAL.withDescription(s"$queueSizeProp: ${t.getMessage}").asException())
      }

  def unaryInput[Req, Res](
      runtime: Runtime[Any],
      impl: (Req, RequestContext, ZServerCall[Res]) => ZIO[Any, StatusException, Unit]
  ): ServerCallHandler[Req, Res] =
    new ZServerCallHandler(runtime, ListenerDriver.makeUnaryInputListener(impl, runtime))

  def streamingInput[Req, Res](
      runtime: Runtime[Any],
      impl: (
          Stream[StatusException, Req],
          RequestContext,
          ZServerCall[Res]
      ) => ZIO[Any, StatusException, Unit]
  ): ServerCallHandler[Req, Res] =
    new ZServerCallHandler(
      runtime,
      ListenerDriver.makeStreamingInputListener(impl)
    )

  def unaryCallHandler[Req, Res](
      runtime: Runtime[Any],
      impl: (Req, RequestContext) => ZIO[Any, StatusException, Res]
  ): ServerCallHandler[Req, Res] =
    unaryInput[Req, Res](
      runtime,
      (req, requestContext, call) => impl(req, requestContext).flatMap[Any, StatusException, Unit](call.sendMessage)
    )

  def serverStreamingCallHandler[Req, Res](
      runtime: Runtime[Any],
      impl: (Req, RequestContext) => ZStream[Any, StatusException, Res]
  ): ServerCallHandler[Req, Res] =
    unaryInput[Req, Res](
      runtime,
      (req: Req, requestContext: RequestContext, call: ZServerCall[Res]) =>
        serverStreamingWithBackpressure(call, impl(req, requestContext))
    )

  def clientStreamingCallHandler[Req, Res](
      runtime: Runtime[Any],
      impl: (Stream[StatusException, Req], RequestContext) => ZIO[Any, StatusException, Res]
  ): ServerCallHandler[Req, Res] =
    streamingInput[Req, Res](
      runtime,
      (req, requestContext, call) => impl(req, requestContext).flatMap[Any, StatusException, Unit](call.sendMessage)
    )

  def bidiCallHandler[Req, Res](
      runtime: Runtime[Any],
      impl: (Stream[Status, Req], RequestContext) => ZStream[Any, StatusException, Res]
  ): ServerCallHandler[Req, Res] =
    streamingInput[Req, Res](
      runtime,
      (req, requestContext, call) => serverStreamingWithBackpressure(call, impl(req, requestContext))
    )

  def serverStreamingWithBackpressure[Res](
      call: ZServerCall[Res],
      stream: ZStream[Any, StatusException, Res]
  ): ZIO[Any, StatusException, Unit] = {
    def takeFromQueue(queue: Dequeue[Exit[Option[StatusException], Res]]): ZIO[Any, StatusException, Unit] =
      queue.takeAll.flatMap(takeFromCache(_, queue))

    def takeFromCache(
        xs: Chunk[Exit[Option[StatusException], Res]],
        queue: Dequeue[Exit[Option[StatusException], Res]]
    ): ZIO[Any, StatusException, Unit] =
      ZIO.suspendSucceed {
        @tailrec def innerLoop(loop: Boolean, i: Int): IO[StatusException, Unit] =
          if (i < xs.length && loop) {
            xs(i) match {
              case Failure(cause) =>
                cause.failureOrCause match {
                  case Left(Some(status)) =>
                    ZIO.fail(status)
                  case Left(None)         =>
                    ZIO.unit
                  case Right(cause)       =>
                    ZIO.failCause(cause)
                }
              case Success(value) =>
                call.call.sendMessage(value)
                // the loop iteration may only continue if the call can
                // still accept elements and we have more elements to send
                innerLoop(call.call.isReady, i + 1)
            }
          } else if (loop)
            // ^ if we reached the end of the chunk but the call can still
            // proceed, we pull from the queue and continue
            takeFromQueue(queue)
          else
            // ^ otherwise, we wait for the call to be ready and then start again
            call.awaitReady *> takeFromCache(xs.drop(i), queue)

        if (xs.isEmpty)
          takeFromQueue(queue)
        else
          innerLoop(true, 0)
      }

    for {
      queueSize <- backpressureQueueSize
      _         <- ZIO
                     .scoped[Any](
                       stream
                         .toQueueOfElements(queueSize)
                         .flatMap(queue => call.awaitReady *> takeFromQueue(queue))
                     )
    } yield ()
  }
}
