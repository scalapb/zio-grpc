package scalapb.zio_grpc

import zio.ZIO
import zio.stream.ZStream
import io.grpc.Status

/** Describes a transformation for all effects and streams of a service.
  *
  * Instances of this class can be used to apply a transformation to all methods of a service to generate a new
  * "decorated" service. This can be used for pre- or post-processing of requests/response.
  */
trait Transform {
  self =>
  def effect[A](io: ZIO[Any, Status, A]): ZIO[Any, Status, A]
  def stream[A](io: ZStream[Any, Status, A]): ZStream[Any, Status, A]

  // Converts this Transform to ZTransform that transforms the effects like this, but
  // leaves the Context unchanged.
  def toZTransform[Context]: ZTransform[Context, Context] = new ZTransform[Context, Context] {
    def effect[A](io: Context => ZIO[Any, Status, A]): Context => ZIO[Any, Status, A] = { c =>
      self.effect(io(c))
    }

    def stream[A](io: Context => ZStream[Any, Status, A]): Context => ZStream[Any, Status, A] = { c =>
      self.stream(io(c))
    }
  }

  def andThen(other: Transform): Transform = new Transform {
    def effect[A](io: ZIO[Any, Status, A]): ZIO[Any, Status, A] = other.effect(self.effect(io))

    def stream[A](io: ZStream[Any, Status, A]): ZStream[Any, Status, A] = other.stream(self.stream(io))
  }
}

object Transform {
  def fromZTransform(ct: ZTransform[Any, Any]) = new Transform {
    def effect[A](io: ZIO[Any, Status, A]): ZIO[Any, Status, A] = ct.effect(_ => io)(())

    def stream[A](io: ZStream[Any, Status, A]): ZStream[Any, Status, A] = ct.stream(_ => io)(())
  }
}

/** Describes a transformation for all effects and streams of a service that has context.
  *
  * Instances of this class can be used to apply a transformation to all methods of a service to generate a new
  * "decorated" service. This can be used for pre- or post-processing of requests/response or to transform the context.
  */
trait ZTransform[+ContextIn, -ContextOut] {
  self =>
  def effect[A](io: ContextIn => ZIO[Any, Status, A]): (ContextOut => ZIO[Any, Status, A])
  def stream[A](io: ContextIn => ZStream[Any, Status, A]): (ContextOut => ZStream[Any, Status, A])

  def andThen[ContextIn2 <: ContextOut, ContextOut2](
      other: ZTransform[ContextIn2, ContextOut2]
  ): ZTransform[ContextIn, ContextOut2] = new ZTransform[ContextIn, ContextOut2] {
    def effect[A](io: ContextIn => ZIO[Any, Status, A]): ContextOut2 => ZIO[Any, Status, A] =
      other.effect(self.effect(io))

    def stream[A](io: ContextIn => ZStream[Any, Status, A]): ContextOut2 => ZStream[Any, Status, A] =
      other.stream(self.stream(io))
  }
}

object ZTransform {
  // Returns a ZTransform that effectfully transforms the context parameter
  def apply[ContextIn, ContextOut](f: ContextOut => ZIO[Any, Status, ContextIn]): ZTransform[ContextIn, ContextOut] =
    new ZTransform[ContextIn, ContextOut] {
      def effect[A](io: ContextIn => ZIO[Any, Status, A]): ContextOut => ZIO[Any, Status, A] = {
        (context: ContextOut) =>
          f(context).flatMap(io)
      }

      def stream[A](io: ContextIn => ZStream[Any, Status, A]): ContextOut => ZStream[Any, Status, A] = {
        (context: ContextOut) =>
          ZStream.fromZIO(f(context)).flatMap(io)
      }
    }
}
