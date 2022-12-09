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
      rmd     <- SafeMetadata.make
      canSend <- TSemaphore.make(1).commit
      zioCall  = new ZServerCall(call, canSend)
      md      <- SafeMetadata.fromMetadata(headers)
      driver  <- mkDriver(zioCall, RequestContext.fromServerCall(md, rmd, call))
      // Why forkDaemon? we need the driver to keep runnning in the background after we return a listener
      // back to grpc-java. If it was just fork, the call to unsafeRun would not return control, so grpc-java
      // won't have a listener to call on.  The driver awaits on the calls to the listener to pass to the user's
      // service.
      _       <- driver.run.forkDaemon
    } yield driver.listener

    runtime.unsafeRun(runner)
  }
}

object ZServerCallHandler {
  private[zio_grpc] val queueSizeProp = "zio-grpc.backpressure-queue-size"

  val backpressureQueueSize: UIO[Int] =
    ZIO.effect(sys.props.get(queueSizeProp).map(_.toInt)).some.orElse(ZIO.succeed(16))

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
      backpressureQueueSize: Int,
      impl: Req => ZIO[Has[RequestContext], Status, Res]
  ): ServerCallHandler[Req, Res] =
    unaryInput[Any, Req, Res](
      runtime,
      (req, requestContext, call) => impl(req).provide(Has(requestContext)).flatMap[Any, Status, Unit](call.sendMessage)
    )

  def serverStreamingCallHandler[Req, Res](
      runtime: Runtime[Any],
      backpressureQueueSize: Int,
      impl: Req => ZStream[Has[RequestContext], Status, Res]
  ): ServerCallHandler[Req, Res] =
    unaryInput[Any, Req, Res](
      runtime,
      (req, requestContext, call) =>
        serverStreamingWithBackpressure(backpressureQueueSize, call, impl(req).provide(Has(requestContext)))
    )

  def clientStreamingCallHandler[Req, Res](
      runtime: Runtime[Any],
      backpressureQueueSize: Int,
      impl: Stream[Status, Req] => ZIO[Has[RequestContext], Status, Res]
  ): ServerCallHandler[Req, Res] =
    streamingInput[Any, Req, Res](
      runtime,
      (req, metadata, call) => impl(req).provide(Has(metadata)).flatMap[Any, Status, Unit](call.sendMessage)
    )

  def bidiCallHandler[Req, Res](
      runtime: Runtime[Any],
      backpressureQueueSize: Int,
      impl: Stream[Status, Req] => ZStream[Has[RequestContext], Status, Res]
  ): ServerCallHandler[Req, Res] =
    streamingInput[Any, Req, Res](
      runtime,
      (req, requestContext, call) =>
        serverStreamingWithBackpressure(backpressureQueueSize, call, impl(req).provide(Has(requestContext)))
    )

  def serverStreamingWithBackpressure[Res](
      backpressureQueueSize: Int,
      call: ZServerCall[Res],
      stream: ZStream[Any, Status, Res]
  ): ZIO[Any, Status, Unit] = {
    def innerLoop(queue: Dequeue[Take[Status, Res]], buffer: Ref[Chunk[Res]]): ZIO[Any, Status, Boolean] =
      buffer
        .modify(chunk => chunk.headOption -> chunk.drop(1))
        .flatMap {
          case None      =>
            queue.take.flatMap(
              _.foldM(ZIO.succeed(false), ZIO.halt(_), buffer.set(_) *> innerLoop(queue, buffer))
            )
          case Some(res) =>
            call.sendMessage(res).as(true)
        }
        .repeatWhileM(res => call.isReady.map(_ && res))

    def outerLoop(queue: Dequeue[Take[Status, Res]])(buffer: Ref[Chunk[Res]]): ZIO[Any, Status, Boolean] =
      (call.awaitReady *> innerLoop(queue, buffer))
        .repeatWhile(identity)

    stream
      .toQueue(backpressureQueueSize)
      .use(queue => Ref.make[Chunk[Res]](Chunk.empty).flatMap(outerLoop(queue)))
      .unit
  }
}
