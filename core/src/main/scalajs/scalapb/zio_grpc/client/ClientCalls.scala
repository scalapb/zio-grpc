package scalapb.zio_grpc.client

import zio.{Chunk, ZIO}
import scalapb.zio_grpc.SafeMetadata
import scalapb.zio_grpc.ZChannel
import io.grpc.MethodDescriptor
import io.grpc.CallOptions
import scalapb.grpcweb.native.ErrorInfo
import io.grpc.Status
import zio.stream.ZStream
import scalapb.grpcweb.native.StatusInfo

object ClientCalls {
  def unaryCall[R, Req, Res](
      channel: ZChannel[R],
      method: MethodDescriptor[Req, Res],
      options: CallOptions,
      headers: SafeMetadata,
      req: Req
  ): ZIO[R, Status, Res] =
    ZIO.async { callback =>
      channel.channel.client.rpcCall[Req, Res](
        channel.channel.baseUrl + "/" + method.fullName,
        req,
        scalajs.js.Dictionary[String](),
        method.methodInfo,
        (errorInfo: ErrorInfo, resp: Res) =>
          if (errorInfo != null)
            callback(ZIO.fail(Status.fromErrorInfo(errorInfo)))
          else callback(ZIO.succeed(resp))
      )
    }

  def serverStreamingCall[R, Req, Res](
      channel: ZChannel[R],
      method: MethodDescriptor[Req, Res],
      options: CallOptions,
      headers: SafeMetadata,
      req: Req
  ): ZStream[R, Status, Res] =
    ZStream.async[R, Status, Res] { cb =>
      channel.channel.client
        .serverStreaming[Req, Res](
          channel.channel.baseUrl + "/" + method.fullName,
          req,
          scalajs.js.Dictionary[String](),
          method.methodInfo
        )
        .on(
          "data",
          { (res: Res) =>
            val _ = cb(ZIO.succeed(Chunk.single(res)))
          }
        )
        .on(
          "error",
          { (ei: ErrorInfo) =>
            val _ = cb(ZIO.fail(Some(Status.fromErrorInfo(ei))))
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
              cb(ZIO.fail(Some(Status.fromStatusInfo(status))))
        )
    }

  def clientStreamingCall[R, R0, Req, Res](
      channel: ZChannel[R],
      method: MethodDescriptor[Req, Res],
      options: CallOptions,
      headers: SafeMetadata,
      req: ZStream[R0, Status, Req]
  ): ZIO[R with R0, Status, Res] =
    ZIO.fail(Status.INTERNAL.withDescription("Not supported"))

  def bidiCall[R, R0, Req, Res](
      channel: ZChannel[R],
      method: MethodDescriptor[Req, Res],
      options: CallOptions,
      headers: SafeMetadata,
      req: ZStream[R0, Status, Req]
  ): ZStream[R with R0, Status, Res] =
    ZStream.fail(Status.INTERNAL.withDescription("Not supported"))
}
