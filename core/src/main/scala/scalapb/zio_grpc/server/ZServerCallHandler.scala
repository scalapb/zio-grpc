package scalapb.zio_grpc.server

import zio._
import io.grpc.ServerCall.Listener
import io.grpc.Status
import io.grpc.Metadata
import zio.stream.Stream
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import scalapb.zio_grpc.server.ZServerCall
import zio.stream.ZStream

class ZServerCallHandler[R, Req, Res](
    runtime: Runtime[R],
    mkDriver: (ZServerCall[Res], Metadata) => URIO[R, CallDriver[R, Req]]
) extends ServerCallHandler[Req, Res] {
  def startCall(
      call: ServerCall[Req, Res],
      headers: Metadata
  ): Listener[Req] = {
    val zioCall = new ZServerCall(call)
    val runner = for {
      driver <- mkDriver(zioCall, headers)
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
      impl: (Req, Metadata, ZServerCall[Res]) => ZIO[R, Status, Unit]
  ): ServerCallHandler[Req, Res] =
    new ZServerCallHandler(runtime, CallDriver.makeUnaryInputCallDriver(impl))

  def streamingInput[R, Req, Res](
      runtime: Runtime[R],
      impl: (
          Stream[Status, Req],
          Metadata,
          ZServerCall[Res]
      ) => ZIO[R, Status, Unit]
  ): ServerCallHandler[Req, Res] =
    new ZServerCallHandler(
      runtime,
      CallDriver.makeStreamingInputCallDriver(impl)
    )

  def unaryCallHandler[R, Req, Res](
      runtime: Runtime[R],
      impl: (Req, Metadata) => ZIO[R, Status, Res]
  ): ServerCallHandler[Req, Res] =
    unaryInput(
      runtime,
      (req, metadata, call) => impl(req, metadata) >>= call.sendMessage
    )

  def unaryCallHandlerR[R, Req, Res](
      runtime: Runtime[R],
      impl: Req => ZIO[Has[Metadata], Status, Res]
  ): ServerCallHandler[Req, Res] =
    unaryInput(
      runtime,
      (req, metadata, call) =>
        impl(req).provide(Has(metadata)) >>= call.sendMessage
    )

  def serverStreamingCallHandler[R, Req, Res](
      runtime: Runtime[R],
      impl: (Req, Metadata) => ZStream[R, Status, Res]
  ): ServerCallHandler[Req, Res] =
    unaryInput(
      runtime,
      (req: Req, metadata: Metadata, call: ZServerCall[Res]) =>
        impl(req, metadata).foreach(call.sendMessage)
    )

  def serverStreamingCallHandlerR[Req, Res](
      runtime: Runtime[Any],
      impl: Req => ZStream[Has[Metadata], Status, Res]
  ): ServerCallHandler[Req, Res] =
    unaryInput(
      runtime,
      (req: Req, metadata: Metadata, call: ZServerCall[Res]) =>
        impl(req).provide(Has(metadata)).foreach(call.sendMessage)
    )

  def clientStreamingCallHandler[R, Req, Res](
      runtime: Runtime[R],
      impl: (Stream[Status, Req], Metadata) => ZIO[R, Status, Res]
  ): ServerCallHandler[Req, Res] =
    streamingInput(
      runtime,
      (req, metadata, call) => impl(req, metadata) >>= call.sendMessage
    )

  def clientStreamingCallHandlerR[Req, Res](
      runtime: Runtime[Any],
      impl: Stream[Status, Req] => ZIO[Has[Metadata], Status, Res]
  ): ServerCallHandler[Req, Res] =
    streamingInput(
      runtime,
      (req, metadata, call) =>
        impl(req).provide(Has(metadata)) >>= call.sendMessage
    )

  def bidiCallHandler[R, Req, Res](
      runtime: Runtime[R],
      impl: (Stream[Status, Req], Metadata) => ZStream[R, Status, Res]
  ): ServerCallHandler[Req, Res] =
    streamingInput(
      runtime,
      (req, metadata, call) => impl(req, metadata).foreach(call.sendMessage)
    )

  def bidiCallHandlerR[Req, Res](
      runtime: Runtime[Any],
      impl: Stream[Status, Req] => ZStream[Has[Metadata], Status, Res]
  ): ServerCallHandler[Req, Res] =
    streamingInput(
      runtime,
      (req, metadata, call) =>
        impl(req).provide(Has(metadata)).foreach(call.sendMessage)
    )
}
