package scalapb.zio_grpc.client

import zio.{Chunk, IO, ZIO}
import scalapb.zio_grpc.ResponseContext
import scalapb.zio_grpc.ResponseFrame
import scalapb.zio_grpc.SafeMetadata
import scalapb.zio_grpc.ZChannel
import io.grpc.{CallOptions, MethodDescriptor, Status, StatusException}
import scalapb.grpcweb.native.ErrorInfo
import zio.stream.ZStream
import scalapb.grpcweb.native.StatusInfo

object ClientCalls {
  object withMetadata {
    def unaryCall[Req, Res](
        channel: ZChannel,
        method: MethodDescriptor[Req, Res],
        options: CallOptions,
        headers: SafeMetadata,
        req: Req
    ): IO[StatusException, ResponseContext[Res]] =
      ZIO.async { callback =>
        channel.channel.client.rpcCall[Req, Res](
          channel.channel.baseUrl + "/" + method.fullName,
          req,
          headers.metadata,
          method.methodInfo,
          (errorInfo: ErrorInfo, resp: Res) =>
            if (errorInfo != null)
              callback(ZIO.fail(new StatusException(Status.fromErrorInfo(errorInfo))))
            else callback(ZIO.succeed(ResponseContext(null, resp, null)))
        )
      }

    def serverStreamingCall[Req, Res](
        channel: ZChannel,
        method: MethodDescriptor[Req, Res],
        options: CallOptions,
        headers: SafeMetadata,
        req: Req
    ): ZStream[Any, StatusException, ResponseFrame[Res]] =
      ZStream.async[Any, StatusException, ResponseFrame[Res]] { cb =>
        channel.channel.client
          .serverStreaming[Req, Res](
            channel.channel.baseUrl + "/" + method.fullName,
            req,
            headers.metadata,
            method.methodInfo
          )
          .on(
            "data",
            { (res: Res) =>
              val _ = cb(ZIO.succeed(Chunk.single(ResponseFrame.Message(res))))
            }
          )
          .on(
            "error",
            { (ei: ErrorInfo) =>
              val _ = cb(ZIO.fail(Some(new StatusException(Status.fromErrorInfo(ei)))))
            }
          )
          .on(
            "end",
            { () =>
              val _ = cb(ZIO.fail(None))
            }
          )
          .on(
            "status",
            (status: StatusInfo) =>
              if (status.code != 0)
                cb(ZIO.fail(Some(new StatusException(Status.fromStatusInfo(status)))))
          )
      }

    def clientStreamingCall[Req, Res](
        channel: ZChannel,
        method: MethodDescriptor[Req, Res],
        options: CallOptions,
        headers: SafeMetadata,
        req: ZStream[Any, StatusException, Req]
    ): IO[StatusException, ResponseContext[Res]] =
      ZIO.fail(new StatusException(Status.INTERNAL.withDescription("Not supported")))

    def bidiCall[Req, Res](
        channel: ZChannel,
        method: MethodDescriptor[Req, Res],
        options: CallOptions,
        headers: SafeMetadata,
        req: ZStream[Any, StatusException, Req]
    ): ZStream[Any, StatusException, ResponseFrame[Res]] =
      ZStream.fail(new StatusException(Status.INTERNAL.withDescription("Not supported")))
  }

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
  ): ZIO[Any, StatusException, Res] =
    ZIO.fail(new StatusException(Status.INTERNAL.withDescription("Not supported")))

  def bidiCall[Req, Res](
      channel: ZChannel,
      method: MethodDescriptor[Req, Res],
      options: CallOptions,
      headers: SafeMetadata,
      req: ZStream[Any, StatusException, Req]
  ): ZStream[Any, StatusException, Res] =
    ZStream.fail(new StatusException(Status.INTERNAL.withDescription("Not supported")))
}
