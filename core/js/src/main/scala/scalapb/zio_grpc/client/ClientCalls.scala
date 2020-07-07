package scalapb.zio_grpc.client

import zio.IO
import zio.stream.Stream
import scalapb.zio_grpc.SafeMetadata
import scalapb.zio_grpc.ZChannel
import scalapb.zio_grpc.GStream
import io.grpc.MethodDescriptor
import io.grpc.CallOptions
import scalapb.grpcweb.native.ErrorInfo
import io.grpc.Status
import zio.stream.ZStream
import zio.ZIO
import zio.Queue
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
  ): ZStream[R, Status, Res] = {
    val e = (for {
      runtime <- ZIO.runtime[R]
      queue <- Queue.unbounded[Either[Option[Status], Res]]
      rpc <- ZIO.effectTotal(
        channel.channel.client
          .rpcCall[Req, Res](
            channel.channel.baseUrl + "/" + method.fullName,
            req,
            scalajs.js.Dictionary[String](),
            method.methodInfo
          )
          .on(
            "data",
            (res: Res) => runtime.unsafeRun(queue.offer(Right(res)).unit)
          )
          .on(
            "status",
            { (status: StatusInfo) =>
              val elem =
                if (status.code != 0) Left(Some(Status.fromStatusInfo(status)))
                else Left(None)
              runtime.unsafeRun(
                queue.offer(elem).unit
              )
            }
          )
          .on(
            "error",
            (ei: ErrorInfo) =>
              runtime.unsafeRun(
                queue.offer(Left(Some(Status.fromErrorInfo(ei)))).unit
              )
          )
      )
    } yield (queue, rpc))

    Stream.fromEffect(e).flatMap {
      case (queue, rpc) =>
        Stream
          .fromQueue(queue)
          .tap {
            case Left(Some(status)) =>
              queue.shutdown *> IO.fail(status)
            case _ => IO.unit
          }
          .collect {
            case Right(v) => v
          }
    }
  }

  def clientStreamingCall[R, Req, Res](
      channel: ZChannel[R],
      method: MethodDescriptor[Req, Res],
      options: CallOptions,
      headers: SafeMetadata,
      req: GStream[Req]
  ): ZIO[R, Status, Res] =
    IO.fail(Status.INTERNAL.withDescription("Not supported"))

  def bidiCall[R, Req, Res](
      channel: ZChannel[R],
      method: MethodDescriptor[Req, Res],
      options: CallOptions,
      headers: SafeMetadata,
      req: GStream[Req]
  ): ZStream[R, Status, Res] =
    Stream.fail(Status.INTERNAL.withDescription("Not supported"))
}
