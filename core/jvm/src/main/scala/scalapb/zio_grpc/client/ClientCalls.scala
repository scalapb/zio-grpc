package scalapb.zio_grpc.client

import scalapb.zio_grpc.GIO
import scalapb.zio_grpc.GStream
import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.Metadata
import zio.Exit
import zio.ZIO
import io.grpc.Status
import zio.stream.Stream
import io.grpc.MethodDescriptor

object ClientCalls {
  def exitHandler[Req, Res](
      call: ZClientCall[Req, Res]
  )(l: Any, ex: Exit[Status, Any]) = anyExitHandler(call)(l, ex)

  // less type safe
  def anyExitHandler[Req, Res](
      call: ZClientCall[Req, Res]
  ) =
    (_: Any, ex: Exit[Any, Any]) => {
      ZIO.when(!ex.succeeded) {
        call.cancel("Interrupted").ignore
      }
    }

  def unaryCall[Req, Res](
      channel: Channel,
      method: MethodDescriptor[Req, Res],
      options: CallOptions,
      headers: Metadata,
      req: Req
  ): GIO[Res] =
    unaryCall(ZClientCall(channel.newCall(method, options)), headers, req)

  private def unaryCall[Req, Res](
      call: ZClientCall[Req, Res],
      headers: Metadata,
      req: Req
  ): GIO[Res] =
    ZIO.bracketExit(UnaryClientCallListener.make[Res])(exitHandler(call)) {
      listener =>
        call.start(listener, headers) *>
          call.request(1) *>
          call.sendMessage(req) *>
          call.halfClose() *>
          listener.getValue.map(_._2)
    }

  def serverStreamingCall[Req, Res](
      channel: Channel,
      method: MethodDescriptor[Req, Res],
      options: CallOptions,
      headers: Metadata,
      req: Req
  ): GStream[Res] =
    serverStreamingCall(
      ZClientCall(channel.newCall(method, options)),
      headers,
      req
    )

  private def serverStreamingCall[Req, Res](
      call: ZClientCall[Req, Res],
      headers: Metadata,
      req: Req
  ): GStream[Res] =
    Stream
      .bracketExit(
        StreamingClientCallListener.make[Res](call)
      )(anyExitHandler(call))
      .flatMap { listener: StreamingClientCallListener[Res] =>
        Stream
          .fromEffect(
            call.start(listener, headers) *>
              call.request(1) *>
              call.sendMessage(req) *>
              call.halfClose()
          )
          .drain ++ listener.stream
      }

  def clientStreamingCall[Req, Res](
      channel: Channel,
      method: MethodDescriptor[Req, Res],
      options: CallOptions,
      headers: Metadata,
      req: GStream[Req]
  ): GIO[Res] =
    clientStreamingCall(
      ZClientCall(channel.newCall(method, options)),
      headers,
      req
    )

  private def clientStreamingCall[Req, Res](
      call: ZClientCall[Req, Res],
      headers: Metadata,
      req: GStream[Req]
  ): GIO[Res] =
    ZIO.bracketExit(UnaryClientCallListener.make[Res])(exitHandler(call)) {
      listener =>
        call.start(listener, headers) *>
          call.request(1) *>
          req.foreach(call.sendMessage) *>
          call.halfClose() *>
          listener.getValue.map(_._2)
    }

  def bidiCall[Req, Res](
      channel: Channel,
      method: MethodDescriptor[Req, Res],
      options: CallOptions,
      headers: Metadata,
      req: GStream[Req]
  ): GStream[Res] =
    bidiCall(ZClientCall(channel.newCall(method, options)), headers, req)

  private def bidiCall[Req, Res](
      call: ZClientCall[Req, Res],
      headers: Metadata,
      req: GStream[Req]
  ): GStream[Res] =
    Stream
      .bracketExit(
        StreamingClientCallListener.make[Res](call)
      )(anyExitHandler(call))
      .flatMap { listener: StreamingClientCallListener[Res] =>
        val init = Stream
          .fromEffect(
            call.start(listener, headers) *>
              call.request(1)
          )
          .drain
        val sendRequestStream = (init ++ req.tap(call.sendMessage) ++ Stream
          .fromEffect(call.halfClose())).drain
        sendRequestStream.merge(listener.stream)
      }
}
