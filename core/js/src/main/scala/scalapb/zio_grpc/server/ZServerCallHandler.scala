package scalapb.zio_grpc.server

import io.grpc.Status
import zio.Has
import zio.Runtime
import zio.ZIO
import zio.stream.{Stream, ZStream}

import scalapb.zio_grpc.RequestContext

trait ServerCallHandler[Req, Res]

object ZServerCallHandler {
  def unaryCallHandler[Req, Res](
      runtime: Runtime[Any],
      impl: Req => ZIO[Has[RequestContext], Status, Res]
  ): ServerCallHandler[Req, Res] = ???

  def serverStreamingCallHandler[Req, Res](
      runtime: Runtime[Any],
      impl: Req => ZStream[Has[RequestContext], Status, Res]
  ): ServerCallHandler[Req, Res] = ???

  def clientStreamingCallHandler[Req, Res](
      runtime: Runtime[Any],
      impl: Stream[Status, Req] => ZIO[Has[RequestContext], Status, Res]
  ): ServerCallHandler[Req, Res] = ???

  def bidiCallHandler[Req, Res](
      runtime: Runtime[Any],
      impl: Stream[Status, Req] => ZStream[Has[RequestContext], Status, Res]
  ): ServerCallHandler[Req, Res] = ???
}
