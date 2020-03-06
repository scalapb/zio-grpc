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
      impl: (Req, ZServerCall[Res]) => ZIO[R, Status, Unit]
  ) =
    new ZServerCallHandler(runtime, CallDriver.makeUnaryInputCallDriver(impl))

  def streamingInput[R, Req, Res](
      runtime: Runtime[R],
      impl: (Stream[Status, Req], ZServerCall[Res]) => ZIO[R, Status, Unit]
  ) =
    new ZServerCallHandler(
      runtime,
      CallDriver.makeStreamingInputCallDriver(impl)
    )

  def unaryCallHandler[R, Req, Res](
      runtime: Runtime[R],
      impl: Req => ZIO[R, Status, Res]
  ): ServerCallHandler[Req, Res] =
    ZServerCallHandler.unaryInput(
      runtime,
      (req, call) => impl(req) >>= call.sendMessage
    )

  def serverStreamingCallHandler[R, Req, Res](
      runtime: Runtime[R],
      impl: Req => ZStream[R, Status, Res]
  ): ServerCallHandler[Req, Res] =
    ZServerCallHandler.unaryInput(
      runtime,
      (req, call) => impl(req).foreach(call.sendMessage)
    )

  def clientStreamingCallHandler[R, Req, Res](
      runtime: Runtime[R],
      impl: Stream[Status, Req] => ZIO[R, Status, Res]
  ): ServerCallHandler[Req, Res] =
    ZServerCallHandler.streamingInput(
      runtime,
      (req, call) => impl(req) >>= call.sendMessage
    )

  def bidiCallHandler[R, Req, Res](
      runtime: Runtime[R],
      impl: Stream[Status, Req] => ZStream[R, Status, Res]
  ): ServerCallHandler[Req, Res] =
    ZServerCallHandler.streamingInput(
      runtime,
      (req, call) => impl(req).foreach(call.sendMessage)
    )
}
