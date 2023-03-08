package scalapb.zio_grpc

import zio.ZIO
import zio.stream.ZStream
import io.grpc.StatusRuntimeException

/** Describes a transformation for all effects and streams of a service.
  *
  * Instances of this class can be used to apply a transformation to all methods of a service to generate a new
  * "decorated" service. This can be used for pre- or post-processing of requests/response.
  */
trait Transform {
  self =>
  def effect[A](io: ZIO[Any, StatusRuntimeException, A]): ZIO[Any, StatusRuntimeException, A]
  def stream[A](io: ZStream[Any, StatusRuntimeException, A]): ZStream[Any, StatusRuntimeException, A]

  // Converts this Transform to ZTransform that transforms the effects like this, but
  // leaves the Context unchanged.
  def toZTransform[Context]: ZTransform[Context, Context] = new ZTransform[Context, Context] {
    def effect[A](io: Context => ZIO[Any, StatusRuntimeException, A]): Context => ZIO[Any, StatusRuntimeException, A] = { c =>
      self.effect(io(c))
    }

    def stream[A](io: Context => ZStream[Any, StatusRuntimeException, A]): Context => ZStream[Any, StatusRuntimeException, A] = { c =>
      self.stream(io(c))
    }
  }

  def andThen(other: Transform): Transform = new Transform {
    def effect[A](io: ZIO[Any, StatusRuntimeException, A]): ZIO[Any, StatusRuntimeException, A] = other.effect(self.effect(io))

    def stream[A](io: ZStream[Any, StatusRuntimeException, A]): ZStream[Any, StatusRuntimeException, A] = other.stream(self.stream(io))
  }
}

object Transform {
  def fromZTransform(ct: ZTransform[Any, Any]) = new Transform {
    def effect[A](io: ZIO[Any, StatusRuntimeException, A]): ZIO[Any, StatusRuntimeException, A] = ct.effect(_ => io)(())

    def stream[A](io: ZStream[Any, StatusRuntimeException, A]): ZStream[Any, StatusRuntimeException, A] = ct.stream(_ => io)(())
  }
}

/** Describes a transformation for all effects and streams of a service that has context.
  *
  * Instances of this class can be used to apply a transformation to all methods of a service to generate a new
  * "decorated" service. This can be used for pre- or post-processing of requests/response or to transform the context.
  */
trait ZTransform[+ContextIn, -ContextOut] {
  self =>
  def effect[A](io: ContextIn => ZIO[Any, StatusRuntimeException, A]): (ContextOut => ZIO[Any, StatusRuntimeException, A])
  def stream[A](io: ContextIn => ZStream[Any, StatusRuntimeException, A]): (ContextOut => ZStream[Any, StatusRuntimeException, A])

  def andThen[ContextIn2 <: ContextOut, ContextOut2](
      other: ZTransform[ContextIn2, ContextOut2]
  ): ZTransform[ContextIn, ContextOut2] = new ZTransform[ContextIn, ContextOut2] {
    def effect[A](io: ContextIn => ZIO[Any, StatusRuntimeException, A]): ContextOut2 => ZIO[Any, StatusRuntimeException, A] =
      other.effect(self.effect(io))

    def stream[A](io: ContextIn => ZStream[Any, StatusRuntimeException, A]): ContextOut2 => ZStream[Any, StatusRuntimeException, A] =
      other.stream(self.stream(io))
  }
}

object ZTransform {
  // Returns a ZTransform that effectfully transforms the context parameter
  def apply[ContextIn, ContextOut](f: ContextOut => ZIO[Any, StatusRuntimeException, ContextIn]): ZTransform[ContextIn, ContextOut] =
    new ZTransform[ContextIn, ContextOut] {
      def effect[A](io: ContextIn => ZIO[Any, StatusRuntimeException, A]): ContextOut => ZIO[Any, StatusRuntimeException, A] = {
        (context: ContextOut) =>
          f(context).flatMap(io)
      }

      def stream[A](io: ContextIn => ZStream[Any, StatusRuntimeException, A]): ContextOut => ZStream[Any, StatusRuntimeException, A] = {
        (context: ContextOut) =>
          ZStream.fromZIO(f(context)).flatMap(io)
      }
    }
}
