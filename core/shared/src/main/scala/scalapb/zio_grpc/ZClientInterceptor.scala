package scalapb.zio_grpc

import io.grpc.MethodDescriptor
import io.grpc.CallOptions
import zio.ZIO
import io.grpc.Status
import io.grpc.Metadata
import scalapb.zio_grpc.client.ZClientCall

abstract class ZClientInterceptor[R] {
  def interceptCall[Req, Res](
      methodDescriptor: MethodDescriptor[Req, Res],
      call: CallOptions,
      clientCall: ZClientCall[R, Req, Res]
  ): ZClientCall[R, Req, Res]
}

object ZClientInterceptor {
  def metadataReplacer[R](
      effect: (MethodDescriptor[_, _], CallOptions) => ZIO[R, Status, Metadata]
  ): ZClientInterceptor[R] =
    new ZClientInterceptor[R] {
      def interceptCall[Req, Res](
          methodDescriptor: MethodDescriptor[Req, Res],
          call: CallOptions,
          clientCall: ZClientCall[R, Req, Res]
      ) =
        ZClientCall.headersTransformer(
          clientCall,
          _ => effect(methodDescriptor, call)
        )
    }
}
