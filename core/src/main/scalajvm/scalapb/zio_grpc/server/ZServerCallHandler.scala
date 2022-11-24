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
import zio.stream.Take

class ZServerCallHandler[R, Req, Res](
    runtime: Runtime[R],
    mkDriver: (ZServerCall[Res], RequestContext) => URIO[R, CallDriver[R, Req]]
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
      driver           <- mkDriver(zioCall, RequestContext.fromServerCall(md, responseMetadata, call))
      // Why forkDaemon? we need the driver to keep runnning in the background after we return a listener
      // back to grpc-java. If it was just fork, the call to unsafeRun would not return control, so grpc-java
      // won't have a listener to call on.  The driver awaits on the calls to the listener to pass to the user's
      // service.
      _                <- driver.run.forkDaemon
    } yield driver.listener

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

  def unaryInput[R, Req, Res](
      runtime: Runtime[R],
      impl: (Req, RequestContext, ZServerCall[Res]) => ZIO[R, Status, Unit]
  ): ServerCallHandler[Req, Res] =
    new ZServerCallHandler(runtime, CallDriver.makeUnaryInputCallDriver(impl))

  def streamingInput[R, Req, Res](
      runtime: Runtime[R],
      impl: (
          Stream[Status, Req],
          RequestContext,
          ZServerCall[Res]
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
      (req, requestContext, call) =>
        impl(req).provideEnvironment(ZEnvironment(requestContext)).flatMap[Any, Status, Unit](call.sendMessage)
    )

  def serverStreamingCallHandler[Req, Res](
      runtime: Runtime[Any],
      impl: Req => ZStream[RequestContext, Status, Res]
  ): ServerCallHandler[Req, Res] =
    unaryInput[Any, Req, Res](
      runtime,
      (req: Req, requestContext: RequestContext, call: ZServerCall[Res]) =>
        serverStreamingWithBackpressure(call, impl(req).provideEnvironment(ZEnvironment(requestContext)))
    )

  def clientStreamingCallHandler[Req, Res](
      runtime: Runtime[Any],
      impl: Stream[Status, Req] => ZIO[RequestContext, Status, Res]
  ): ServerCallHandler[Req, Res] =
    streamingInput[Any, Req, Res](
      runtime,
      (req, requestContext, call) =>
        impl(req).provideEnvironment(ZEnvironment(requestContext)).flatMap[Any, Status, Unit](call.sendMessage)
    )

  def bidiCallHandler[Req, Res](
      runtime: Runtime[Any],
      impl: Stream[Status, Req] => ZStream[RequestContext, Status, Res]
  ): ServerCallHandler[Req, Res] =
    streamingInput[Any, Req, Res](
      runtime,
      (req, requestContext, call) =>
        serverStreamingWithBackpressure(call, impl(req).provideEnvironment(ZEnvironment(requestContext)))
    )

  def serverStreamingWithBackpressure[Res](
      call: ZServerCall[Res],
      stream: ZStream[Any, Status, Res]
  ): ZIO[Any, Status, Unit] = {
    def innerLoop(queue: Dequeue[Take[Status, Res]], buffer: Ref[Chunk[Res]]): ZIO[Any, Status, Boolean] =
      buffer
        .modify(chunk => chunk.headOption -> chunk.drop(1))
        .flatMap {
          case None      =>
            queue.take.flatMap(
              _.foldZIO(ZIO.succeed(false), ZIO.failCause(_), buffer.set(_) *> innerLoop(queue, buffer))
            )
          case Some(res) =>
            call.sendMessage(res).as(true)
        }
        .repeatWhileZIO(res => call.isReady.map(_ && res))

    def outerLoop(queue: Dequeue[Take[Status, Res]])(buffer: Ref[Chunk[Res]]): ZIO[Any, Status, Boolean] =
      (call.awaitReady *> innerLoop(queue, buffer))
        .repeatWhile(identity)

    for {
      queueSize <- backpressureQueueSize
      _         <- ZIO.scoped(
                     stream
                       .toQueue(queueSize)
                       .flatMap { queue =>
                         Ref.make[Chunk[Res]](Chunk.empty).flatMap(outerLoop(queue))
                       }
                   )
    } yield ()
  }
}
