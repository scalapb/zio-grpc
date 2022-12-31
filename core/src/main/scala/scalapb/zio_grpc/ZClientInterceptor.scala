package scalapb.zio_grpc

import io.grpc.MethodDescriptor
import io.grpc.CallOptions
import zio.IO
import io.grpc.Status
import scalapb.zio_grpc.client.ZClientCall

abstract class ZClientInterceptor {
  self =>
  def interceptCall[Req, Res](
      methodDescriptor: MethodDescriptor[Req, Res],
      call: CallOptions,
      clientCall: ZClientCall[Req, Res]
  ): ZClientCall[Req, Res]
}

object ZClientInterceptor {
  def headersReplacer(
      effect: (
          MethodDescriptor[_, _],
          CallOptions
      ) => IO[Status, SafeMetadata]
  ): ZClientInterceptor =
    new ZClientInterceptor {
      def interceptCall[Req, Res](
          methodDescriptor: MethodDescriptor[Req, Res],
          call: CallOptions,
          clientCall: ZClientCall[Req, Res]
      ): ZClientCall[Req, Res] =
        ZClientCall.headersTransformer(
          clientCall,
          _ => effect(methodDescriptor, call)
        )
    }

  def headersUpdater(
      effect: (
          MethodDescriptor[_, _],
          CallOptions,
          SafeMetadata
      ) => IO[Status, Unit]
  ): ZClientInterceptor =
    new ZClientInterceptor {
      def interceptCall[Req, Res](
          methodDescriptor: MethodDescriptor[Req, Res],
          call: CallOptions,
          clientCall: ZClientCall[Req, Res]
      ): ZClientCall[Req, Res] =
        ZClientCall.headersTransformer(
          clientCall,
          md => effect(methodDescriptor, call, md).as(md)
        )
    }
}
