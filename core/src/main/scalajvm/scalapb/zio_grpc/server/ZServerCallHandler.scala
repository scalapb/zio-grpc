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
      canSend <- TSemaphore.make(1).commit
      driver  <- mkDriver(zioCall, canSend, RequestContext.fromServerCall(md, rmd, call))
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
      impl: Req => ZIO[Has[RequestContext], Status, Res]
  ): ServerCallHandler[Req, Res] =
    unaryInput[Any, Req, Res](
      runtime,
      (req, requestContext, call, _) =>
        impl(req).provide(Has(requestContext)).flatMap[Any, Status, Unit](call.sendMessage)
    )

  def serverStreamingCallHandler[Req, Res](
      runtime: Runtime[Any],
      impl: Req => ZStream[Has[RequestContext], Status, Res]
  ): ServerCallHandler[Req, Res] =
    unaryInput[Any, Req, Res](
      runtime,
      (req, requestContext, call, canSend) =>
        serverStreamingWithBackpressure(call, canSend, requestContext, impl(req).provide(Has(requestContext)))
    )

  def clientStreamingCallHandler[Req, Res](
      runtime: Runtime[Any],
      impl: Stream[Status, Req] => ZIO[Has[RequestContext], Status, Res]
  ): ServerCallHandler[Req, Res] =
    streamingInput[Any, Req, Res](
      runtime,
      (req, metadata, call, _) => impl(req).provide(Has(metadata)).flatMap[Any, Status, Unit](call.sendMessage)
    )

  def bidiCallHandler[Req, Res](
      runtime: Runtime[Any],
      impl: Stream[Status, Req] => ZStream[Has[RequestContext], Status, Res]
  ): ServerCallHandler[Req, Res] =
    streamingInput[Any, Req, Res](
      runtime,
      (req, requestContext, call, canSend) =>
        serverStreamingWithBackpressure(call, canSend, requestContext, impl(req).provide(Has(requestContext)))
    )

  def serverStreamingWithBackpressure[Res](
      call: ZServerCall[Res],
      canSend: TSemaphore,
      ctx: RequestContext,
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
        .repeatWhile(_ && call.isReady)

    def outerLoop(queue: Dequeue[Take[Status, Res]])(buffer: Ref[Chunk[Res]]): ZIO[Any, Status, Boolean] =
      (canSend.acquire.commit *> innerLoop(queue, buffer))
        .repeatWhile(identity)

    stream
      .toQueue(16)
      .use(queue => Ref.make[Chunk[Res]](Chunk.empty).flatMap(outerLoop(queue)))
      .unit
  }
}
