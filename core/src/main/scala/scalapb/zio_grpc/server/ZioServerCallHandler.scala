package scalapb.zio_grpc.server

import zio._
import io.grpc.ServerCallHandler
import io.grpc.ServerCall
import io.grpc.Metadata
import io.grpc.ServerCall.Listener
import zio.stream.ZStream
import io.grpc.Status

object ZioServerCallHandler {
  def handlerMaker[R, Req, Res, InputType](
      runtime: Runtime[R],
      listenerFactory: ZServerCall[_] => URIO[
        R,
        CommonListener[R, Req, InputType]
      ]
  )(
      impl: (
          CommonListener[R, Req, InputType],
          ZServerCall[Res],
          Metadata
      ) => ZIO[R, Nothing, Unit]
  ) = new ServerCallHandler[Req, Res] {
    def startCall(
        call: ServerCall[Req, Res],
        headers: Metadata
    ): Listener[Req] = {
      val runner = for {
        zioCall <- ZServerCall.makeFrom(call)
        l <- listenerFactory(zioCall)
        _ <- impl(l, zioCall, headers).fork
      } yield l

      runtime.unsafeRun(runner)
    }
  }

  def unaryCallHandler[R, Req, Res](
      runtime: Runtime[R],
      impl: Req => ZIO[R, Status, Res]
  ): ServerCallHandler[Req, Res] =
    handlerMaker(runtime, UnaryCallListener.make[R, Req])(
      (cl, c: ZServerCall[Res], m) => cl.serveUnary(c)(impl, m)
    )

  def serverStreamingCallHandler[R, Req, Res](
      runtime: Runtime[R],
      impl: Req => ZStream[R, Status, Res]
  ): ServerCallHandler[Req, Res] =
    handlerMaker(runtime, UnaryCallListener.make[R, Req])(
      (cl, c: ZServerCall[Res], m) => cl.serveStreaming(c)(impl, m)
    )

  def clientStreamingCallHandler[R, Req, Res](
      runtime: Runtime[R],
      impl: ZStream[R, Status, Req] => ZIO[R, Status, Res]
  ): ServerCallHandler[Req, Res] =
    handlerMaker(runtime, StreamingCallListener.make[R, Req])(
      (cl, c: ZServerCall[Res], m) => cl.serveUnary(c)(impl, m)
    )

  def bidiCallHandler[R, Req, Res](
      runtime: Runtime[R],
      impl: ZStream[R, Status, Req] => ZStream[R, Status, Res]
  ): ServerCallHandler[Req, Res] =
    handlerMaker(runtime, StreamingCallListener.make[R, Req])(
      (cl, c: ZServerCall[Res], m) => cl.serveStreaming(c)(impl, m)
    )
}
