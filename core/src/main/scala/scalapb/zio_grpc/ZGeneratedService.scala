package scalapb.zio_grpc

import zio.UIO
import zio.IO
import io.grpc.ServerServiceDefinition
import io.grpc.Status

trait ZGeneratedService[-C, S[-_]] {
  this: S[C] =>

  def transform[COut](zt: ZTransform[C, COut]): S[COut]

  def transform(t: Transform): S[C] = transform[C](t.toZTransform[C])

  def transformContextZIO[ContextOut](f: ContextOut => IO[Status, C]): S[ContextOut] = transform(ZTransform(f))

  def transformContext[ContextOut](f: ContextOut => C): S[ContextOut] = transformContextZIO(c => zio.ZIO.succeed(f(c)))
}

trait GeneratedService    {
  type WithContext[-_]

  def withContext: WithContext[Any]

  def transform(t: Transform): WithContext[Any]

  def transform[C](zt: ZTransform[Any, C]): WithContext[C]
}

trait GenericBindable[-S] {
  def bind(s: S): UIO[ServerServiceDefinition]
}
