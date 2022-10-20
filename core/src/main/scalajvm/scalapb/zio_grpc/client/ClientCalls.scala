package scalapb.zio_grpc.client

import io.grpc.{CallOptions, MethodDescriptor, Status}
import scalapb.zio_grpc.{ResponseContext, ResponseFrame, SafeMetadata, ZChannel}
import zio.stream.{Stream, ZStream}
import zio.{Exit, ZIO}

object ClientCalls {
  object withMetadata {

    private def unaryCall[R, Req, Res](
        call: ZClientCall[R, Req, Res],
        headers: SafeMetadata,
        req: Req
    ): ZIO[R, Status, ResponseContext[Res]] =
      ZIO.bracketExit(UnaryClientCallListener.make[Res])(exitHandler(call)) { listener =>
        call.start(listener, headers) *>
          call.request(1) *>
          call.sendMessage(req) *>
          call.halfClose() *>
          listener.getValue
      }

    def unaryCall[R, Req, Res](
        channel: ZChannel[R],
        method: MethodDescriptor[Req, Res],
        options: CallOptions,
        headers: SafeMetadata,
        req: Req
    ): ZIO[R, Status, ResponseContext[Res]] =
      channel
        .newCall(method, options)
        .flatMap(unaryCall(_, headers, req))

    private def serverStreamingCall[R, Req, Res](
        call: ZClientCall[R, Req, Res],
        headers: SafeMetadata,
        req: Req
    ): ZStream[R, Status, ResponseFrame[Res]] =
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

    def serverStreamingCall[R, Req, Res](
        channel: ZChannel[R],
        method: MethodDescriptor[Req, Res],
        options: CallOptions,
        headers: SafeMetadata,
        req: Req
    ): ZStream[R, Status, ResponseFrame[Res]] =
      Stream
        .fromEffect(channel.newCall(method, options))
        .flatMap(serverStreamingCall(_, headers, req))

    private def clientStreamingCall[R, R0, Req, Res](
        call: ZClientCall[R, Req, Res],
        headers: SafeMetadata,
        req: ZStream[R0, Status, Req]
    ): ZIO[R with R0, Status, ResponseContext[Res]] =
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

    def clientStreamingCall[R, R0, Req, Res](
        channel: ZChannel[R],
        method: MethodDescriptor[Req, Res],
        options: CallOptions,
        headers: SafeMetadata,
        req: ZStream[R0, Status, Req]
    ): ZIO[R with R0, Status, ResponseContext[Res]] =
      channel
        .newCall(method, options)
        .flatMap(
          clientStreamingCall(
            _,
            headers,
            req
          )
        )

    private def bidiCall[R, R0, Req, Res](
        call: ZClientCall[R, Req, Res],
        headers: SafeMetadata,
        req: ZStream[R0, Status, Req]
    ): ZStream[R with R0, Status, ResponseFrame[Res]] =
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
          val finish            = Stream
            .fromEffect(call.halfClose())
          val sendRequestStream = (init ++ req.tap(call.sendMessage) ++ finish).drain
          sendRequestStream.merge(listener.stream, ZStream.TerminationStrategy.Right)
        }

    def bidiCall[R, R0, Req, Res](
        channel: ZChannel[R],
        method: MethodDescriptor[Req, Res],
        options: CallOptions,
        headers: SafeMetadata,
        req: ZStream[R0, Status, Req]
    ): ZStream[R with R0, Status, ResponseFrame[Res]] =
      Stream
        .fromEffect(
          channel.newCall(method, options)
        )
        .flatMap(bidiCall(_, headers, req))

  }

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
    withMetadata.unaryCall(channel, method, options, headers, req).map(_.response)

  def serverStreamingCall[R, Req, Res](
      channel: ZChannel[R],
      method: MethodDescriptor[Req, Res],
      options: CallOptions,
      headers: SafeMetadata,
      req: Req
  ): ZStream[R, Status, Res]     =
    withMetadata
      .serverStreamingCall(channel, method, options, headers, req)
      .collect { case ResponseFrame.Message(x) => x }

  def clientStreamingCall[R, R0, Req, Res](
      channel: ZChannel[R],
      method: MethodDescriptor[Req, Res],
      options: CallOptions,
      headers: SafeMetadata,
      req: ZStream[R0, Status, Req]
  ): ZIO[R with R0, Status, Res] =
    withMetadata.clientStreamingCall(channel, method, options, headers, req).map(_.response)

  def bidiCall[R, R0, Req, Res](
      channel: ZChannel[R],
      method: MethodDescriptor[Req, Res],
      options: CallOptions,
      headers: SafeMetadata,
      req: ZStream[R0, Status, Req]
  ): ZStream[R with R0, Status, Res] =
    withMetadata
      .bidiCall(channel, method, options, headers, req)
      .collect { case ResponseFrame.Message(x) => x }
}
