package scalapb.zio_grpc.client

import zio.IO
import zio.stream.Stream
import scalapb.zio_grpc.{GIO, GStream}
import io.grpc.MethodDescriptor
import io.grpc.CallOptions
import io.grpc.Metadata
import io.grpc.Channel
import scalapb.grpcweb.native.ErrorInfo
import io.grpc.Status

object ClientCalls {
  def unaryCall[Req, Res](
      channel: Channel,
      method: MethodDescriptor[Req, Res],
      options: CallOptions,
      headers: Metadata,
      req: Req
  ): GIO[Res] =
    IO.effectAsync { callback =>
      channel.client.rpcCall[Req, Res](
        channel.baseUrl + "/" + method.fullName,
        req,
        scalajs.js.Dictionary(),
        method.methodInfo,
        (errorInfo: ErrorInfo, resp: Res) =>
          if (errorInfo != null)
            callback(IO.fail(Status.fromErrorInfo(errorInfo)))
          else callback(IO.succeed(resp))
      )
    }

  def serverStreamingCall[Req, Res](
      channel: Channel,
      method: MethodDescriptor[Req, Res],
      options: CallOptions,
      headers: Metadata,
      req: Req
  ): GStream[Res] =
    Stream.fail(Status.INTERNAL.withDescription("Unimplemented"))

  def clientStreamingCall[Req, Res](
      channel: Channel,
      method: MethodDescriptor[Req, Res],
      options: CallOptions,
      headers: Metadata,
      req: GStream[Req]
  ): GStream[Res] =
    Stream.fail(Status.INTERNAL.withDescription("Not supported"))

  def bidiCall[Req, Res](
      channel: Channel,
      method: MethodDescriptor[Req, Res],
      options: CallOptions,
      headers: Metadata,
      req: GStream[Req]
  ): GStream[Res] =
    Stream.fail(Status.INTERNAL.withDescription("Not supported"))
}
