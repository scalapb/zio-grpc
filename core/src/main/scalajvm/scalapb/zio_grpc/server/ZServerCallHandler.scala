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
import zio.stream.ZSink
import zio.stream.ZChannel
import scalapb.zio_grpc.GIO

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
  private[zio_grpc] val queueSizeProp = "zio_grpc.backpressure_queue_size"

  val backpressureQueueSize: IO[StatusException, Int] =
    ZIO
      .config(zio.Config.int("backpressure_queue_size").nested("zio_grpc").withDefault(16))
      .mapError { (e: zio.Config.Error) =>
        Status.INTERNAL.withDescription(s"$queueSizeProp: ${e.getMessage}").asException()
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
      impl: (Stream[StatusException, Req], RequestContext) => ZStream[Any, StatusException, Res]
  ): ServerCallHandler[Req, Res] =
    streamingInput[Req, Res](
      runtime,
      (req, requestContext, call) => serverStreamingWithBackpressure(call, impl(req, requestContext))
    )

  def serverStreamingWithBackpressure[Res](
      call: ZServerCall[Res],
      stream: ZStream[Any, StatusException, Res]
  ): ZIO[Any, StatusException, Unit] = {
    val backpressureSink = {
      def go: ZChannel[Any, ZNothing, Chunk[Res], Any, StatusException, Chunk[Res], Unit] =
        ZChannel.readWithCause(
          xs =>
            ZChannel.fromZIO(GIO.attempt(xs.foreach(call.call.sendMessage))) *>
              ZChannel.suspend(if (call.call.isReady()) go else ZChannel.fromZIO(call.awaitReady) *> go),
          c => ZChannel.failCause(c),
          _ => ZChannel.unit
        )

      ZSink.fromChannel(go)
    }

    for {
      queueSize <- backpressureQueueSize
      _         <- stream.buffer(queueSize).run(backpressureSink)
    } yield ()
  }
}
