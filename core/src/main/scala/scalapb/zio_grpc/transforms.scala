package scalapb.zio_grpc

import zio.ZIO
import zio.stream.ZStream
import io.grpc.StatusException

/** Describes a transformation for all effects and streams of a service.
  *
  * Instances of this class can be used to apply a transformation to all methods of a service to generate a new
  * "decorated" service. This can be used for pre- or post-processing of requests/response.
  */
trait Transform {
  self =>
  def effect[A](io: ZIO[Any, StatusException, A]): ZIO[Any, StatusException, A]
  def stream[A](io: ZStream[Any, StatusException, A]): ZStream[Any, StatusException, A]

  // Converts this Transform to ZTransform that transforms the effects like this, but
  // leaves the Context unchanged.
  def toZTransform[Context]: ZTransform[Context, Context] = new ZTransform[Context, Context] {
    def effect[A](
        io: Context => ZIO[Any, StatusException, A]
    ): Context => ZIO[Any, StatusException, A] = { c =>
      self.effect(io(c))
    }

    def stream[A](
        io: Context => ZStream[Any, StatusException, A]
    ): Context => ZStream[Any, StatusException, A] = { c =>
      self.stream(io(c))
    }
  }

  def andThen(other: Transform): Transform = new Transform {
    def effect[A](io: ZIO[Any, StatusException, A]): ZIO[Any, StatusException, A] =
      other.effect(self.effect(io))

    def stream[A](io: ZStream[Any, StatusException, A]): ZStream[Any, StatusException, A] =
      other.stream(self.stream(io))
  }
}

object Transform {
  def fromZTransform(ct: ZTransform[Any, Any]) = new Transform {
    def effect[A](io: ZIO[Any, StatusException, A]): ZIO[Any, StatusException, A] = ct.effect(_ => io)(())

    def stream[A](io: ZStream[Any, StatusException, A]): ZStream[Any, StatusException, A] =
      ct.stream(_ => io)(())
  }
}

/** Describes a transformation for all effects and streams of a service that has context.
  *
  * Instances of this class can be used to apply a transformation to all methods of a service to generate a new
  * "decorated" service. This can be used for pre- or post-processing of requests/response or to transform the context.
  */
trait ZTransform[+ContextIn, -ContextOut] {
  self =>
  def effect[A](
      io: ContextIn => ZIO[Any, StatusException, A]
  ): (ContextOut => ZIO[Any, StatusException, A])
  def stream[A](
      io: ContextIn => ZStream[Any, StatusException, A]
  ): (ContextOut => ZStream[Any, StatusException, A])

  def andThen[ContextIn2 <: ContextOut, ContextOut2](
      other: ZTransform[ContextIn2, ContextOut2]
  ): ZTransform[ContextIn, ContextOut2] = new ZTransform[ContextIn, ContextOut2] {
    def effect[A](
        io: ContextIn => ZIO[Any, StatusException, A]
    ): ContextOut2 => ZIO[Any, StatusException, A] =
      other.effect(self.effect(io))

    def stream[A](
        io: ContextIn => ZStream[Any, StatusException, A]
    ): ContextOut2 => ZStream[Any, StatusException, A] =
      other.stream(self.stream(io))
  }
}

object ZTransform {
  // Returns a ZTransform that effectfully transforms the context parameter
  def apply[ContextIn, ContextOut](
      f: ContextOut => ZIO[Any, StatusException, ContextIn]
  ): ZTransform[ContextIn, ContextOut] =
    new ZTransform[ContextIn, ContextOut] {
      def effect[A](
          io: ContextIn => ZIO[Any, StatusException, A]
      ): ContextOut => ZIO[Any, StatusException, A] = { (context: ContextOut) =>
        f(context).flatMap(io)
      }

      def stream[A](
          io: ContextIn => ZStream[Any, StatusException, A]
      ): ContextOut => ZStream[Any, StatusException, A] = { (context: ContextOut) =>
        ZStream.fromZIO(f(context)).flatMap(io)
      }
    }
}
