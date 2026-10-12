package scalapb.zio_grpc.client

import io.grpc.{
  CallOptions,
  ClientCall,
  ForwardingClientCallListener,
  Metadata,
  MethodDescriptor,
  Status,
  StatusException
}
import scalapb.zio_grpc.{ResponseContext, ResponseFrame, SafeMetadata, ZChannel}
import zio.stream.ZStream
import zio.{Exit, IO, Promise, Queue, UIO, Unsafe, ZIO}

object ClientCalls {
  object withMetadata {

    private def unaryCall[Req, Res](
        call: ZClientCall[Req, Res],
        headers: SafeMetadata,
        req: Req
    ): IO[StatusException, ResponseContext[Res]] =
      ZIO.acquireReleaseExitWith(UnaryClientCallListener.make[Res])(exitHandler(call)) { listener =>
        call.start(listener, headers) *>
          call.request(1) *>
          call.sendMessage(req) *>
          call.halfClose() *>
          listener.getValue
      }

    def unaryCall[Req, Res](
        channel: ZChannel,
        method: MethodDescriptor[Req, Res],
        options: CallOptions,
        headers: SafeMetadata,
        req: Req
    ): IO[StatusException, ResponseContext[Res]] =
      channel
        .newCall(method, options)
        .flatMap(unaryCall(_, headers, req))

    private def serverStreamingCall[Req, Res](
        call: ZClientCall[Req, Res],
        headers: SafeMetadata,
        prefetch: Option[Int],
        req: Req
    ): ZStream[Any, StatusException, ResponseFrame[Res]] =
      ZStream
        .acquireReleaseExitWith(
          StreamingClientCallListener.make[Res](call, prefetch)
        )(anyExitHandler[Req, Res](call))
        .flatMap { (listener: StreamingClientCallListener[Res]) =>
          ZStream.unwrap(
            (call.start(listener, headers) *>
              call.request(prefetch.getOrElse(1)) *>
              call.sendMessage(req) *>
              call.halfClose()).as(listener.stream)
          )
        }

    def serverStreamingCall[Req, Res](
        channel: ZChannel,
        method: MethodDescriptor[Req, Res],
        options: CallOptions,
        headers: SafeMetadata,
        req: Req
    ): ZStream[Any, StatusException, ResponseFrame[Res]] =
      ZStream.unwrap(channel.newCall(method, options).map(serverStreamingCall(_, headers, channel.prefetch, req)))

    // Initialize the call before returning the interruptible request-sending effect.
    // Only calls with streaming requests allocate readiness and closure signals.
    private def startStreamingRequest[Req, Res](
        call: ZClientCall[Req, Res],
        listener: ClientCall.Listener[Res],
        headers: SafeMetadata,
        responseDemand: Int,
        input: ZStream[Any, StatusException, Req]
    ): IO[StatusException, IO[StatusException, Unit]] =
      for {
        runtime <- ZIO.runtime[Any]
        ready   <- Queue.sliding[Unit](1)
        closed  <- Promise.make[Nothing, Unit]
        _       <- call.start(
                     new ForwardingClientCallListener.SimpleForwardingClientCallListener[Res](listener) {
                       private def unsafeRun(effect: UIO[Any]): Unit =
                         Unsafe.unsafe { implicit unsafe =>
                           runtime.unsafe.run(effect).getOrThrowFiberFailure()
                           ()
                         }

                       override def onReady(): Unit = unsafeRun(ready.offer(()))

                       override def onClose(status: Status, trailers: Metadata): Unit = {
                         // Stop input even when downstream response consumption is blocked.
                         unsafeRun(closed.succeed(()))
                         listener.onClose(status, trailers)
                       }
                     },
                     headers
                   )
        _       <- call.request(responseDemand)
      } yield {
        def sendMessage(message: Req): IO[StatusException, Unit] =
          call.isReady.flatMap {
            case true  => call.sendMessage(message)
            // Retain notifications arriving between isReady and take; recheck stale ones.
            case false => ready.take *> sendMessage(message)
          }

        (input.runForeach(sendMessage) *> call.halfClose().unlessZIO(closed.isDone)).unit
          .raceFirst(closed.await)
          .unlessZIO(closed.isDone)
          .unit
      }

    private def clientStreamingCall[Req, Res](
        call: ZClientCall[Req, Res],
        headers: SafeMetadata,
        req: ZStream[Any, StatusException, Req]
    ): IO[StatusException, ResponseContext[Res]] =
      ZIO.acquireReleaseExitWith(UnaryClientCallListener.make[Res])(exitHandler(call)) { listener =>
        startStreamingRequest(call, listener, headers, 1, req).flatMap { send =>
          // Finishing input only half-closes the call; the server owns the final result.
          (send *> ZIO.never).raceFirst(listener.getValue)
        }
      }

    def clientStreamingCall[Req, Res](
        channel: ZChannel,
        method: MethodDescriptor[Req, Res],
        options: CallOptions,
        headers: SafeMetadata,
        req: ZStream[Any, StatusException, Req]
    ): IO[StatusException, ResponseContext[Res]] =
      channel
        .newCall(method, options)
        .flatMap(clientStreamingCall(_, headers, req))

    private def bidiCall[Req, Res](
        call: ZClientCall[Req, Res],
        headers: SafeMetadata,
        prefetch: Option[Int],
        req: ZStream[Any, StatusException, Req]
    ): ZStream[Any, StatusException, ResponseFrame[Res]] =
      ZStream
        .acquireReleaseExitWith(
          StreamingClientCallListener.make[Res](call, prefetch)
        )(anyExitHandler(call))
        .flatMap { (listener: StreamingClientCallListener[Res]) =>
          ZStream.unwrap {
            startStreamingRequest(call, listener, headers, prefetch.getOrElse(1), req).map { send =>
              ZStream.execute(send).merge(listener.stream, ZStream.HaltStrategy.Right)
            }
          }
        }

    def bidiCall[Req, Res](
        channel: ZChannel,
        method: MethodDescriptor[Req, Res],
        options: CallOptions,
        headers: SafeMetadata,
        req: ZStream[Any, StatusException, Req]
    ): ZStream[Any, StatusException, ResponseFrame[Res]] =
      ZStream.unwrap(channel.newCall(method, options).map(bidiCall(_, headers, channel.prefetch, req)))
  }

  def exitHandler[Req, Res](
      call: ZClientCall[Req, Res]
  )(l: Any, ex: Exit[StatusException, Any]) =
    ZIO.when(!ex.isSuccess) {
      anyExitHandler(call)(l, ex)
    }

  // less type safe
  def anyExitHandler[Req, Res](
      call: ZClientCall[Req, Res]
  ) =
    (_: Any, ex: Exit[Any, Any]) => call.cancel("Interrupted").ignore

  def unaryCall[Req, Res](
      channel: ZChannel,
      method: MethodDescriptor[Req, Res],
      options: CallOptions,
      headers: SafeMetadata,
      req: Req
  ): IO[StatusException, Res] =
    withMetadata.unaryCall(channel, method, options, headers, req).map(_.response)

  def serverStreamingCall[Req, Res](
      channel: ZChannel,
      method: MethodDescriptor[Req, Res],
      options: CallOptions,
      headers: SafeMetadata,
      req: Req
  ): ZStream[Any, StatusException, Res] =
    withMetadata
      .serverStreamingCall(channel, method, options, headers, req)
      .collect { case ResponseFrame.Message(x) => x }

  def clientStreamingCall[Req, Res](
      channel: ZChannel,
      method: MethodDescriptor[Req, Res],
      options: CallOptions,
      headers: SafeMetadata,
      req: ZStream[Any, StatusException, Req]
  ): IO[StatusException, Res] =
    withMetadata.clientStreamingCall(channel, method, options, headers, req).map(_.response)

  def bidiCall[Req, Res](
      channel: ZChannel,
      method: MethodDescriptor[Req, Res],
      options: CallOptions,
      headers: SafeMetadata,
      req: ZStream[Any, StatusException, Req]
  ): ZStream[Any, StatusException, Res] =
    withMetadata
      .bidiCall(channel, method, options, headers, req)
      .collect { case ResponseFrame.Message(x) => x }
}
