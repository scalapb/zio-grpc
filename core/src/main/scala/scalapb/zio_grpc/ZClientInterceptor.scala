package scalapb.zio_grpc

import io.grpc.MethodDescriptor
import io.grpc.CallOptions
import zio.ZIO
import io.grpc.Status
import scalapb.zio_grpc.client.ZClientCall
import zio.ZEnvironment

abstract class ZClientInterceptor[R] {
  self =>
  def interceptCall[Req, Res](
      methodDescriptor: MethodDescriptor[Req, Res],
      call: CallOptions,
      clientCall: ZClientCall[R, Req, Res]
  ): ZClientCall[R, Req, Res]

  def provideEnvironment(r: => ZEnvironment[R]): ZClientInterceptor[Any] =
    new ZClientInterceptor[Any] {
      def interceptCall[Req, Res](
          methodDescriptor: MethodDescriptor[Req, Res],
          call: CallOptions,
          clientCall: ZClientCall[Any, Req, Res]
      ): ZClientCall[Any, Req, Res] =
        self.interceptCall(methodDescriptor, call, clientCall).provideEnvironment(r)
    }
}

object ZClientInterceptor {
  def headersReplacer[R](
      effect: (
          MethodDescriptor[_, _],
          CallOptions
      ) => ZIO[R, Status, SafeMetadata]
  ): ZClientInterceptor[R] =
    new ZClientInterceptor[R] {
      def interceptCall[Req, Res](
          methodDescriptor: MethodDescriptor[Req, Res],
          call: CallOptions,
          clientCall: ZClientCall[R, Req, Res]
      ): ZClientCall[R, Req, Res] =
        ZClientCall.headersTransformer(
          clientCall,
          _ => effect(methodDescriptor, call)
        )
    }

  def headersUpdater[R](
      effect: (
          MethodDescriptor[_, _],
          CallOptions,
          SafeMetadata
      ) => ZIO[R, Status, Unit]
  ): ZClientInterceptor[R] =
    new ZClientInterceptor[R] {
      def interceptCall[Req, Res](
          methodDescriptor: MethodDescriptor[Req, Res],
          call: CallOptions,
          clientCall: ZClientCall[R, Req, Res]
      ): ZClientCall[R, Req, Res] =
        ZClientCall.headersTransformer(
          clientCall,
          md => effect(methodDescriptor, call, md).as(md)
        )
    }
}
