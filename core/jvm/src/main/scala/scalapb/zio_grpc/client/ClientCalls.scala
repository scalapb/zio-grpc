package scalapb.zio_grpc.client

import io.grpc.CallOptions
import zio.Exit
import zio.ZIO
import io.grpc.Status
import zio.stream.Stream
import io.grpc.MethodDescriptor
import scalapb.zio_grpc.ZChannel
import zio.stream.ZStream
import scalapb.zio_grpc.SafeMetadata

object ClientCalls {
  def exitHandler[R, Req, Res](
      call: ZClientCall[R, Req, Res]
  )(l: Any, ex: Exit[Status, Any]) = anyExitHandler(call)(l, ex)

  // less type safe
  def anyExitHandler[R, Req, Res](
      call: ZClientCall[R, Req, Res]
  ) =
    (_: Any, ex: Exit[Any, Any]) => {
      ZIO.when(!ex.succeeded) {
        call.cancel("Interrupted").ignore
      }
    }

  def unaryCall[R, Req, Res](
      channel: ZChannel[R],
      method: MethodDescriptor[Req, Res],
      options: CallOptions,
      headers: SafeMetadata,
      req: Req
  ): ZIO[R, Status, Res] =
    channel.newCall(method, options) >>= (
      unaryCall(_, headers, req)
    )

  private def unaryCall[R, Req, Res](
      call: ZClientCall[R, Req, Res],
      headers: SafeMetadata,
      req: Req
  ): ZIO[R, Status, Res] =
    ZIO.bracketExit(UnaryClientCallListener.make[R, Res](call))(exitHandler(call)) { listener =>
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
    ZStream.fromEffect(channel.newCall(method, options)) >>= (
      serverStreamingCall(_, headers, req)
    )

  private def serverStreamingCall[R, Req, Res](
      call: ZClientCall[R, Req, Res],
      headers: SafeMetadata,
      req: Req
  ): ZStream[R, Status, Res] =
    Stream
      .bracketExit(
        StreamingClientCallListener.make[R, Res](call)
      )(anyExitHandler[R, Req, Res](call))
      .flatMap { listener: StreamingClientCallListener[R, Res] =>
        Stream
          .fromEffect(
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
    channel.newCall(method, options) >>= (
      clientStreamingCall(_, headers, req)
    )

  private def clientStreamingCall[R, R0, Req, Res](
      call: ZClientCall[R, Req, Res],
      headers: SafeMetadata,
      req: ZStream[R0, Status, Req]
  ): ZIO[R with R0, Status, Res] =
    ZIO.bracketExit(UnaryClientCallListener.make[R, Res](call))(exitHandler(call)) { listener =>
      call.start(listener, headers) *>
        call.request(1) *>
        req.foreach(call.sendMessageWhenReady) *>
        call.halfClose() *>
        listener.getValue.map(_._2)
    }

  def bidiCall[R, R0, Req, Res](
      channel: ZChannel[R],
      method: MethodDescriptor[Req, Res],
      options: CallOptions,
      headers: SafeMetadata,
      req: ZStream[R0, Status, Req]
  ): ZStream[R with R0, Status, Res] =
    ZStream.fromEffect(channel.newCall(method, options)) >>= (
      bidiCall(_, headers, req)
    )

  private def bidiCall[R, R0, Req, Res](
      call: ZClientCall[R, Req, Res],
      headers: SafeMetadata,
      req: ZStream[R0, Status, Req]
  ): ZStream[R with R0, Status, Res] =
    Stream
      .bracketExit(
        StreamingClientCallListener.make[R, Res](call)
      )(anyExitHandler(call))
      .flatMap { listener: StreamingClientCallListener[R, Res] =>
        val init              = Stream
          .fromEffect(
            call.start(listener, headers) *>
              call.request(1)
          )
        val sendRequestStream = (init ++ req.tap(call.sendMessageWhenReady) ++ Stream
          .fromEffect(call.halfClose())).drain
        sendRequestStream.merge(listener.stream)
      }
}
