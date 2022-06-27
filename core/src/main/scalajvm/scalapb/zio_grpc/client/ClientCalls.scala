package scalapb.zio_grpc.client

import io.grpc.{CallOptions, MethodDescriptor, Status}
import scalapb.zio_grpc.{SafeMetadata, ZChannel}
import zio.stream.ZStream
import zio.{Exit, ZIO}

object ClientCalls {
  def exitHandler[R, Req, Res](
      call: ZClientCall[R, Req, Res]
  )(l: Any, ex: Exit[Status, Any]) = anyExitHandler(call)(l, ex)

  // less type safe
  def anyExitHandler[R, Req, Res](
      call: ZClientCall[R, Req, Res]
  ) =
    (_: Any, ex: Exit[Any, Any]) =>
      ZIO.when(!ex.isSuccess) {
        call.cancel("Interrupted").ignore
      }

  def unaryCall[R, Req, Res](
      channel: ZChannel[R],
      method: MethodDescriptor[Req, Res],
      options: CallOptions,
      headers: SafeMetadata,
      req: Req
  ): ZIO[R, Status, Res] =
    channel
      .newCall(method, options)
      .flatMap(unaryCall(_, headers, req))

  private def unaryCall[R, Req, Res](
      call: ZClientCall[R, Req, Res],
      headers: SafeMetadata,
      req: Req
  ): ZIO[R, Status, Res] =
    ZIO.acquireReleaseExitWith(UnaryClientCallListener.make[Res])(exitHandler(call)) { listener =>
      call.start(listener, headers) *>
        call.request(1) *>
        call.sendMessage(req) *>
        call.halfClose() *>
        listener.getValue.map(_._2)
    }

  def serverStreamingCall[R, Req, Res](
      channel: ZChannel[R],
      method: MethodDescriptor[Req, Res],
      options: CallOptions,
      headers: SafeMetadata,
      req: Req
  ): ZStream[R, Status, Res] =
    ZStream
      .fromZIO(channel.newCall(method, options))
      .flatMap(serverStreamingCall(_, headers, req))

  private def serverStreamingCall[R, Req, Res](
      call: ZClientCall[R, Req, Res],
      headers: SafeMetadata,
      req: Req
  ): ZStream[R, Status, Res] =
    ZStream
      .acquireReleaseExitWith(
        StreamingClientCallListener.make[R, Res](call)
      )(anyExitHandler[R, Req, Res](call))
      .flatMap { (listener: StreamingClientCallListener[R, Res]) =>
        ZStream
          .fromZIO(
            call.start(listener, headers) *>
              call.request(1) *>
              call.sendMessage(req) *>
              call.halfClose()
          )
          .drain ++ listener.stream
      }

  def clientStreamingCall[R, R0, Req, Res](
      channel: ZChannel[R],
      method: MethodDescriptor[Req, Res],
      options: CallOptions,
      headers: SafeMetadata,
      req: ZStream[R0, Status, Req]
  ): ZIO[R with R0, Status, Res] =
    channel
      .newCall(method, options)
      .flatMap(
        clientStreamingCall(
          _,
          headers,
          req
        )
      )

  private def clientStreamingCall[R, R0, Req, Res](
      call: ZClientCall[R, Req, Res],
      headers: SafeMetadata,
      req: ZStream[R0, Status, Req]
  ): ZIO[R with R0, Status, Res] =
    ZIO.acquireReleaseExitWith(UnaryClientCallListener.make[Res])(exitHandler(call)) { listener =>
      val callStream   = req.tap(call.sendMessage).drain ++ ZStream.fromZIO(call.halfClose()).drain
      val resultStream = ZStream.fromZIO(listener.getValue)

      call.start(listener, headers) *>
        call.request(1) *>
        callStream
          .merge(resultStream)
          .runCollect
          .map(res => res.last._2)
    }

  def bidiCall[R, R0, Req, Res](
      channel: ZChannel[R],
      method: MethodDescriptor[Req, Res],
      options: CallOptions,
      headers: SafeMetadata,
      req: ZStream[R0, Status, Req]
  ): ZStream[R with R0, Status, Res] =
    ZStream
      .fromZIO(
        channel.newCall(method, options)
      )
      .flatMap(bidiCall(_, headers, req))

  private def bidiCall[R, R0, Req, Res](
      call: ZClientCall[R, Req, Res],
      headers: SafeMetadata,
      req: ZStream[R0, Status, Req]
  ): ZStream[R with R0, Status, Res] =
    ZStream
      .acquireReleaseExitWith(
        StreamingClientCallListener.make[R, Res](call)
      )(anyExitHandler(call))
      .flatMap { (listener: StreamingClientCallListener[R, Res]) =>
        val init              = ZStream
          .fromZIO(
            call.start(listener, headers) *>
              call.request(1)
          )
        val sendRequestStream = (init ++ req.tap(call.sendMessage) ++ ZStream
          .fromZIO(call.halfClose())).drain
        sendRequestStream.merge(listener.stream)
      }
}
