package scalapb.zio_grpc.client

import zio.{Chunk, IO, ZIO}
import zio.stream.Stream
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
    IO.effectAsync { callback =>
      channel.channel.client.rpcCall[Req, Res](
        channel.channel.baseUrl + "/" + method.fullName,
        req,
        scalajs.js.Dictionary[String](),
        method.methodInfo,
        (errorInfo: ErrorInfo, resp: Res) =>
          if (errorInfo != null)
            callback(IO.fail(Status.fromErrorInfo(errorInfo)))
          else callback(IO.succeed(resp))
      )
    }

  def serverStreamingCall[R, Req, Res](
      channel: ZChannel[R],
      method: MethodDescriptor[Req, Res],
      options: CallOptions,
      headers: SafeMetadata,
      req: Req
  ): ZStream[R, Status, Res] =
    Stream.effectAsync[R, Status, Res] { cb =>
      channel.channel.client
        .serverStreaming[Req, Res](
          channel.channel.baseUrl + "/" + method.fullName,
          req,
          scalajs.js.Dictionary[String](),
          method.methodInfo
        )
        .on("data", (res: Res) => cb(ZIO.succeed(Chunk.single(res))))
        .on("error", (ei: ErrorInfo) => cb(ZIO.fail(Some(Status.fromErrorInfo(ei)))))
        .on("end", () => cb(ZIO.fail(None)))
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
    IO.fail(Status.INTERNAL.withDescription("Not supported"))

  def bidiCall[R, R0, Req, Res](
      channel: ZChannel[R],
      method: MethodDescriptor[Req, Res],
      options: CallOptions,
      headers: SafeMetadata,
      req: ZStream[R0, Status, Req]
  ): ZStream[R with R0, Status, Res] =
    Stream.fail(Status.INTERNAL.withDescription("Not supported"))
}
