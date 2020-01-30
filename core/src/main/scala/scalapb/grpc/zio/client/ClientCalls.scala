package scalapb.grpc.zio.client

import scalapb.grpc.zio.GIO
import scalapb.grpc.zio.GStream
import scalapb.grpc.zio.GRStream
import io.grpc.Metadata
import zio.Exit
import zio.ZIO
import io.grpc.Status
import zio.stream.ZStream

object ClientCalls {
  def exitHandler[Req, Res](
      call: ZClientCall[Req, Res]
  )(l: Any, ex: Exit[Status, Any]) = anyExitHandler(call)(l, ex)

  // less type safe
  def anyExitHandler[Req, Res](
      call: ZClientCall[Req, Res]
  ) = (_: Any, ex: Exit[Any, Any]) =>
    ZIO.when(ex.interrupted) {
      call.cancel("Interrupted").ignore
    }

  def unaryCall[Req, Res](
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
      call: ZClientCall[Req, Res],
      headers: Metadata,
      req: Req
  ): GStream[Res] =
    ZStream
      .bracketExit[Any, Status, StreamingClientCallListener[Res]](
        StreamingClientCallListener.make[Res](call)
      )(anyExitHandler(call))
      .flatMap { listener: StreamingClientCallListener[Res] =>
        ZStream
          .fromEffect(
            call.start(listener, headers) *>
              call.request(1) *>
              call.sendMessage(req) *>
              call.halfClose()
          )
          .drain ++ listener.stream
      }

  def bidiCall[R, Req, Res](
      call: ZClientCall[Req, Res],
      headers: Metadata,
      req: GRStream[R, Req]
  ): GRStream[R, Res] =
    ZStream
      .bracketExit[R, Status, StreamingClientCallListener[Res]](
        StreamingClientCallListener.make[Res](call)
      )(anyExitHandler(call))
      .flatMap { listener: StreamingClientCallListener[Res] =>
        val init = ZStream
          .fromEffect(
            call.start(listener, headers) *>
              call.request(1)
          )
          .drain
        val sendRequestStream = (init ++ req.tap(call.sendMessage) ++ ZStream
          .fromEffect(call.halfClose())).drain
        sendRequestStream.merge(listener.stream)
      }
}
