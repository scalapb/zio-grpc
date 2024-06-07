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

  // Converts this Transform to GTransform that transforms the effects like this, but
  // leaves the Context unchanged.
  def toGTransform[Context]: GTransform[Context, StatusException, Context, StatusException] =
    new GTransform[Context, StatusException, Context, StatusException] {
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

  def compose(other: Transform): Transform = other.andThen(self)
}

object Transform {
  def fromGTransform(ct: GTransform[Any, StatusException, Any, StatusException]) = new Transform {
    def effect[A](io: ZIO[Any, StatusException, A]): ZIO[Any, StatusException, A] = ct.effect(_ => io)(())

    def stream[A](io: ZStream[Any, StatusException, A]): ZStream[Any, StatusException, A] =
      ct.stream(_ => io)(())
  }
}

/** Describes a transformation for all effects and streams of a generic service.
  *
  * Instances of this class can be used to apply a transformation to all methods of a service to generate a new
  * "decorated" service. This can be used for pre- or post-processing of requests/response or to transform the context.
  */
trait GTransform[+ContextIn, -ErrorIn, -ContextOut, +ErrorOut] {
  self =>
  def effect[A](
      io: ContextIn => ZIO[Any, ErrorIn, A]
  ): (ContextOut => ZIO[Any, ErrorOut, A])
  def stream[A](
      io: ContextIn => ZStream[Any, ErrorIn, A]
  ): (ContextOut => ZStream[Any, ErrorOut, A])

  def andThen[ContextIn2 <: ContextOut, ErrorIn2 >: ErrorOut, ContextOut2, ErrorOut2](
      other: GTransform[ContextIn2, ErrorIn2, ContextOut2, ErrorOut2]
  ): GTransform[ContextIn, ErrorIn, ContextOut2, ErrorOut2] =
    new GTransform[ContextIn, ErrorIn, ContextOut2, ErrorOut2] {
      def effect[A](
          io: ContextIn => ZIO[Any, ErrorIn, A]
      ): ContextOut2 => ZIO[Any, ErrorOut2, A] =
        other.effect(self.effect(io))

      def stream[A](
          io: ContextIn => ZStream[Any, ErrorIn, A]
      ): ContextOut2 => ZStream[Any, ErrorOut2, A] =
        other.stream(self.stream(io))
    }

  def compose[ContextIn2, ErrorIn2, ContextOut2 >: ContextIn, ErrorOut2 <: ErrorIn](
      other: GTransform[ContextIn2, ErrorIn2, ContextOut2, ErrorOut2]
  ): GTransform[ContextIn2, ErrorIn2, ContextOut, ErrorOut] = other.andThen(self)
}

object GTransform {

  def identity[C, E]: GTransform[C, E, C, E] = new GTransform[C, E, C, E] {
    def effect[A](io: C => ZIO[Any, E, A]): C => ZIO[Any, E, A]         = io
    def stream[A](io: C => ZStream[Any, E, A]): C => ZStream[Any, E, A] = io
  }

  // Returns a GTransform that effectfully transforms the context parameter
  def apply[ContextIn, Error, ContextOut](
      f: ContextOut => ZIO[Any, Error, ContextIn]
  ): GTransform[ContextIn, Error, ContextOut, Error] =
    new GTransform[ContextIn, Error, ContextOut, Error] {
      def effect[A](
          io: ContextIn => ZIO[Any, Error, A]
      ): ContextOut => ZIO[Any, Error, A] = { (context: ContextOut) =>
        f(context).flatMap(io)
      }

      def stream[A](
          io: ContextIn => ZStream[Any, Error, A]
      ): ContextOut => ZStream[Any, Error, A] = { (context: ContextOut) =>
        ZStream.fromZIO(f(context)).flatMap(io)
      }
    }

  // Returns a GTransform that maps the error parameter.
  def mapError[C, E1, E2](f: E1 => E2): GTransform[C, E1, C, E2] = new GTransform[C, E1, C, E2] {
    def effect[A](io: C => zio.ZIO[Any, E1, A]): C => zio.ZIO[Any, E2, A]                       = { (context: C) =>
      io(context).mapError(f)
    }
    def stream[A](io: C => zio.stream.ZStream[Any, E1, A]): C => zio.stream.ZStream[Any, E2, A] = { (context: C) =>
      io(context).mapError(f)
    }
  }

  // Returns a GTransform that effectfully maps the error parameter.
  def mapErrorZIO[C, E1, E2](f: E1 => zio.UIO[E2]): GTransform[C, E1, C, E2] = new GTransform[C, E1, C, E2] {
    def effect[A](io: C => zio.ZIO[Any, E1, A]): C => zio.ZIO[Any, E2, A]                       = { (context: C) =>
      io(context).flatMapError(f)
    }
    def stream[A](io: C => zio.stream.ZStream[Any, E1, A]): C => zio.stream.ZStream[Any, E2, A] = { (context: C) =>
      io(context).catchAll(e => ZStream.fromZIO(f(e).flatMap(ZIO.fail(_))))
    }
  }
}

object ZTransform {
  def apply[ContextIn, ContextOut](
      f: ContextOut => ZIO[Any, StatusException, ContextIn]
  ): ZTransform[ContextIn, ContextOut] = GTransform(f)
}
