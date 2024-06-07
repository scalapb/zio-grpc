package scalapb.zio_grpc

import zio.UIO
import zio.IO
import io.grpc.{ServerServiceDefinition, StatusException}

trait GenericGeneratedService[-C, +E, S[-_, +_]] {
  this: S[C, E] =>

  def transform[COut, ErrOut](zt: GTransform[C, E, COut, ErrOut]): S[COut, ErrOut]

  def transform(t: Transform)(implicit ev: E <:< StatusException): S[C, StatusException] =
    transform[C, StatusException](GTransform.mapError(ev(_)).andThen(t.toGTransform[C]))

  def transformContextZIO[ContextOut, E2 >: E](f: ContextOut => IO[E2, C]): S[ContextOut, E2] = transform(
    GTransform(f)
  )

  def transformContext[ContextOut](f: ContextOut => C): S[ContextOut, E] =
    transformContextZIO(c => zio.ZIO.succeed(f(c)))

  // Transform the error type of the service by applying the given function.
  def mapError[E1](f: E => E1): S[C, E1] = transform(GTransform.mapError[C, E, E1](f))

  // Effectfully transforms the error type of the service by applying the given function.
  def mapErrorZIO[E1](f: E => UIO[E1]): S[C, E1] = transform(GTransform.mapErrorZIO[C, E, E1](f))
}

trait GeneratedService {
  type Generic[-_, +_]

  def asGeneric: Generic[Any, StatusException]

  def transform(t: Transform): Generic[Any, StatusException]

  def transform[C, E](zt: GTransform[Any, StatusException, C, E]): Generic[C, E]
}

trait GenericBindable[-S] {
  def bind(s: S): UIO[ServerServiceDefinition]
}
