package scalapb.zio_grpc.client

import io.grpc.{CallOptions, Metadata, MethodDescriptor, Status}
import scalapb.zio_grpc.{SafeMetadata, ZChannel}
import zio.stream.{Stream, ZStream}
import zio.{Exit, ZIO}

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
    unaryCallWithMetadata(channel, method, options, headers, req).map(_._2)

  def unaryCallWithMetadata[R, Req, Res](
      channel: ZChannel[R],
      method: MethodDescriptor[Req, Res],
      options: CallOptions,
      headers: SafeMetadata,
      req: Req
  ): ZIO[R, Status, (Metadata, Res)] =
    channel
      .newCall(method, options)
      .flatMap(unaryCall(_, headers, req))

  private def unaryCall[R, Req, Res](
      call: ZClientCall[R, Req, Res],
      headers: SafeMetadata,
      req: Req
  ): ZIO[R, Status, (Metadata, Res)] =
    ZIO.bracketExit(UnaryClientCallListener.make[Res])(exitHandler(call)) { listener =>
      call.start(listener, headers) *>
        call.request(1) *>
        call.sendMessage(req) *>
        call.halfClose() *>
        listener.getValue
    }

  def serverStreamingCall[R, Req, Res](
      channel: ZChannel[R],
      method: MethodDescriptor[Req, Res],
      options: CallOptions,
      headers: SafeMetadata,
      req: Req
  ): ZStream[R, Status, Res]                   =
    serverStreamingCallWithMetadata(channel, method, options, headers, req)
      .collect { case Right(x) => x }

  def serverStreamingCallWithMetadata[R, Req, Res](
      channel: ZChannel[R],
      method: MethodDescriptor[Req, Res],
      options: CallOptions,
      headers: SafeMetadata,
      req: Req
  ): ZStream[R, Status, Either[Metadata, Res]] =
    Stream
      .fromEffect(channel.newCall(method, options))
      .flatMap(serverStreamingCall(_, headers, req))

  private def serverStreamingCall[R, Req, Res](
      call: ZClientCall[R, Req, Res],
      headers: SafeMetadata,
      req: Req
  ): ZStream[R, Status, Either[Metadata, Res]] =
    Stream
      .bracketExit(
        StreamingClientCallListener.make[R, Res](call)
      )(anyExitHandler[R, Req, Res](call))
      .flatMap { (listener: StreamingClientCallListener[R, Res]) =>
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
    clientStreamingCallWithMetadata(channel, method, options, headers, req).map(_._2)

  def clientStreamingCallWithMetadata[R, R0, Req, Res](
      channel: ZChannel[R],
      method: MethodDescriptor[Req, Res],
      options: CallOptions,
      headers: SafeMetadata,
      req: ZStream[R0, Status, Req]
  ): ZIO[R with R0, Status, (Metadata, Res)] =
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
  ): ZIO[R with R0, Status, (Metadata, Res)] =
    ZIO.bracketExit(UnaryClientCallListener.make[Res])(exitHandler(call)) { listener =>
      val callStream   = req.tap(call.sendMessage).drain ++ ZStream.fromEffect(call.halfClose()).drain
      val resultStream = ZStream.fromEffect(listener.getValue)

      call.start(listener, headers) *>
        call.request(1) *>
        callStream
          .merge(resultStream)
          .runCollect
          .map(res => res.last)
    }

  def bidiCall[R, R0, Req, Res](
      channel: ZChannel[R],
      method: MethodDescriptor[Req, Res],
      options: CallOptions,
      headers: SafeMetadata,
      req: ZStream[R0, Status, Req]
  ): ZStream[R with R0, Status, Res]                   =
    bidiCallWithMetadata(channel, method, options, headers, req)
      .collect { case Right(x) => x }

  def bidiCallWithMetadata[R, R0, Req, Res](
      channel: ZChannel[R],
      method: MethodDescriptor[Req, Res],
      options: CallOptions,
      headers: SafeMetadata,
      req: ZStream[R0, Status, Req]
  ): ZStream[R with R0, Status, Either[Metadata, Res]] =
    Stream
      .fromEffect(
        channel.newCall(method, options)
      )
      .flatMap(bidiCall(_, headers, req))

  private def bidiCall[R, R0, Req, Res](
      call: ZClientCall[R, Req, Res],
      headers: SafeMetadata,
      req: ZStream[R0, Status, Req]
  ): ZStream[R with R0, Status, Either[Metadata, Res]] =
    Stream
      .bracketExit(
        StreamingClientCallListener.make[R, Res](call)
      )(anyExitHandler(call))
      .flatMap { (listener: StreamingClientCallListener[R, Res]) =>
        val init              = Stream
          .fromEffect(
            call.start(listener, headers) *>
              call.request(1)
          )
        val sendRequestStream = (init ++ req.tap(call.sendMessage) ++ Stream
          .fromEffect(call.halfClose())).drain
        sendRequestStream.merge(listener.stream)
      }
}
