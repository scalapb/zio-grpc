package scalapb.zio_grpc.server

import zio._
import io.grpc.ServerCall.Listener
import io.grpc.Status
import zio.stream.Stream
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import scalapb.zio_grpc.server.ZServerCall
import zio.stream.ZStream
import scalapb.zio_grpc.RequestContext
import io.grpc.Metadata

class ZServerCallHandler[R, Req, Res](
    runtime: Runtime[R],
    mkDriver: (ZServerCall[Res], RequestContext) => URIO[R, CallDriver[R, Req]]
) extends ServerCallHandler[Req, Res] {
  def startCall(
      call: ServerCall[Req, Res],
      headers: Metadata
  ): Listener[Req] = {
    val zioCall = new ZServerCall(call)
    val runner = for {
      driver <- mkDriver(zioCall, RequestContext.fromServerCall(headers, call))
      // Why forkDaemon? we need the driver to keep runnning in the background after we return a listener
      // back to grpc-java. If it was just fork, the call to unsafeRun would not return control, so grpc-java
      // won't have a listener to call on.  The driver awaits on the calls to the listener to pass to the user's
      // service.
      _ <- driver.run.forkDaemon
    } yield driver.listener

    runtime.unsafeRun(runner)
  }
}

object ZServerCallHandler {
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
      impl: Req => ZIO[Has[RequestContext], Status, Res]
  ): ServerCallHandler[Req, Res] =
    unaryInput(
      runtime,
      (req, metadata, call) =>
        impl(req).provide(Has(metadata)) >>= call.sendMessage
    )

  def serverStreamingCallHandler[Req, Res](
      runtime: Runtime[Any],
      impl: Req => ZStream[Has[RequestContext], Status, Res]
  ): ServerCallHandler[Req, Res] =
    unaryInput(
      runtime,
      (req: Req, metadata: RequestContext, call: ZServerCall[Res]) =>
        impl(req).provide(Has(metadata)).foreach(call.sendMessage)
    )

  def clientStreamingCallHandler[Req, Res](
      runtime: Runtime[Any],
      impl: Stream[Status, Req] => ZIO[Has[RequestContext], Status, Res]
  ): ServerCallHandler[Req, Res] =
    streamingInput(
      runtime,
      (req, metadata, call) =>
        impl(req).provide(Has(metadata)) >>= call.sendMessage
    )

  def bidiCallHandler[Req, Res](
      runtime: Runtime[Any],
      impl: Stream[Status, Req] => ZStream[Has[RequestContext], Status, Res]
  ): ServerCallHandler[Req, Res] =
    streamingInput(
      runtime,
      (req, metadata, call) =>
        impl(req).provide(Has(metadata)).foreach(call.sendMessage)
    )
}
