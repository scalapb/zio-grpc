package scalapb.zio_grpc.server

import io.grpc.StatusException
import zio.Runtime
import zio.ZIO
import zio.stream.{Stream, ZStream}

import scalapb.zio_grpc.RequestContext

trait ServerCallHandler[Req, Res]

object ZServerCallHandler {
  def unaryCallHandler[Req, Res](
      runtime: Runtime[Any],
      impl: (Req, RequestContext) => ZIO[Any, StatusException, Res]
  ): ServerCallHandler[Req, Res] = ???

  def serverStreamingCallHandler[Req, Res](
      runtime: Runtime[Any],
      impl: (Req, RequestContext) => ZStream[Any, StatusException, Res]
  ): ServerCallHandler[Req, Res] = ???

  def clientStreamingCallHandler[Req, Res](
      runtime: Runtime[Any],
      impl: (Stream[StatusException, Req], RequestContext) => ZIO[Any, StatusException, Res]
  ): ServerCallHandler[Req, Res] = ???

  def bidiCallHandler[Req, Res](
      runtime: Runtime[Any],
      impl: (Stream[StatusException, Req], RequestContext) => ZStream[Any, StatusException, Res]
  ): ServerCallHandler[Req, Res] = ???
}
