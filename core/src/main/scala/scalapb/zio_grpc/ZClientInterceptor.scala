package scalapb.zio_grpc

import io.grpc.{CallOptions, MethodDescriptor, StatusRuntimeException}
import scalapb.zio_grpc.client.ZClientCall
import zio.IO

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
      ) => IO[StatusRuntimeException, SafeMetadata]
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
      ) => IO[StatusRuntimeException, Unit]
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
