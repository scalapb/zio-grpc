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
  def effect[A, B](io: A => ZIO[Any, StatusException, B]): A => ZIO[Any, StatusException, B]
  def stream[A, B](io: A => ZStream[Any, StatusException, B]): A => ZStream[Any, StatusException, B]

  // Converts this Transform to GTransform that transforms the effects like this, but
  // leaves the Context unchanged.
  def toGTransform[Context]: GTransform[Context, StatusException, Context, StatusException] =
    new GTransform[Context, StatusException, Context, StatusException] {
      def effect[A, B](
          io: (A, Context) => ZIO[Any, StatusException, B]
      ): (A, Context) => ZIO[Any, StatusException, B] = { (a, c) =>
        self.effect[A, B](io(_, c))(a)
      }

      def stream[A, B](
          io: (A, Context) => ZStream[Any, StatusException, B]
      ): (A, Context) => ZStream[Any, StatusException, B] = { (a, c) =>
        self.stream(io(_, c))(a)
      }
    }

  def andThen(other: Transform): Transform = new Transform {
    def effect[A, B](io: A => ZIO[Any, StatusException, B]): A => ZIO[Any, StatusException, B] =
      other.effect(self.effect(io))

    def stream[A, B](io: A => ZStream[Any, StatusException, B]): A => ZStream[Any, StatusException, B] =
      other.stream(self.stream(io))
  }

  def compose(other: Transform): Transform = other.andThen(self)
}

object Transform {
  def fromGTransform(ct: GTransform[Any, StatusException, Any, StatusException]) = new Transform {
    def effect[A, B](io: A => ZIO[Any, StatusException, B]): A => ZIO[Any, StatusException, B] = { a =>
      ct.effect((a, _) => io(a))(a, ())
    }

    def stream[A, B](io: A => ZStream[Any, StatusException, B]): A => ZStream[Any, StatusException, B] = { a =>
      ct.stream((a, _) => io(a))(a, ())
    }
  }
}

/** Describes a transformation for all effects and streams of a generic service.
  *
  * Instances of this class can be used to apply a transformation to all methods of a service to generate a new
  * "decorated" service. This can be used for pre- or post-processing of requests/response or to transform the context.
  */
trait GTransform[+ContextIn, -ErrorIn, -ContextOut, +ErrorOut] {
  self =>
  def effect[A, B](
      io: (A, ContextIn) => ZIO[Any, ErrorIn, B]
  ): ((A, ContextOut) => ZIO[Any, ErrorOut, B])
  def stream[A, B](
      io: (A, ContextIn) => ZStream[Any, ErrorIn, B]
  ): ((A, ContextOut) => ZStream[Any, ErrorOut, B])

  def andThen[ContextIn2 <: ContextOut, ErrorIn2 >: ErrorOut, ContextOut2, ErrorOut2](
      other: GTransform[ContextIn2, ErrorIn2, ContextOut2, ErrorOut2]
  ): GTransform[ContextIn, ErrorIn, ContextOut2, ErrorOut2] =
    new GTransform[ContextIn, ErrorIn, ContextOut2, ErrorOut2] {
      def effect[A, B](
          io: (A, ContextIn) => ZIO[Any, ErrorIn, B]
      ): (A, ContextOut2) => ZIO[Any, ErrorOut2, B] =
        other.effect(self.effect(io))

      def stream[A, B](
          io: (A, ContextIn) => ZStream[Any, ErrorIn, B]
      ): (A, ContextOut2) => ZStream[Any, ErrorOut2, B] =
        other.stream(self.stream(io))
    }

  def compose[ContextIn2, ErrorIn2, ContextOut2 >: ContextIn, ErrorOut2 <: ErrorIn](
      other: GTransform[ContextIn2, ErrorIn2, ContextOut2, ErrorOut2]
  ): GTransform[ContextIn2, ErrorIn2, ContextOut, ErrorOut] = other.andThen(self)
}

object GTransform {

  def identity[C, E]: GTransform[C, E, C, E] = new GTransform[C, E, C, E] {
    def effect[A, B](io: (A, C) => ZIO[Any, E, B]): (A, C) => ZIO[Any, E, B]         = io
    def stream[A, B](io: (A, C) => ZStream[Any, E, B]): (A, C) => ZStream[Any, E, B] = io
  }

  // Returns a GTransform that effectfully transforms the context parameter
  def apply[ContextIn, Error, ContextOut](
      f: ContextOut => ZIO[Any, Error, ContextIn]
  ): GTransform[ContextIn, Error, ContextOut, Error] =
    new GTransform[ContextIn, Error, ContextOut, Error] {
      def effect[A, B](
          io: (A, ContextIn) => ZIO[Any, Error, B]
      ): (A, ContextOut) => ZIO[Any, Error, B] = { (a: A, context: ContextOut) =>
        f(context).flatMap(io(a, _))
      }

      def stream[A, B](
          io: (A, ContextIn) => ZStream[Any, Error, B]
      ): (A, ContextOut) => ZStream[Any, Error, B] = { (a: A, context: ContextOut) =>
        ZStream.fromZIO(f(context)).flatMap(io(a, _))
      }
    }

  // Returns a GTransform that maps the error parameter.
  def mapError[C, E1, E2](f: E1 => E2): GTransform[C, E1, C, E2] = new GTransform[C, E1, C, E2] {
    def effect[A, B](io: (A, C) => zio.ZIO[Any, E1, B]): (A, C) => zio.ZIO[Any, E2, B]                       = { (a: A, context: C) =>
      io(a, context).mapError(f)
    }
    def stream[A, B](io: (A, C) => zio.stream.ZStream[Any, E1, B]): (A, C) => zio.stream.ZStream[Any, E2, B] = {
      (a: A, context: C) =>
        io(a, context).mapError(f)
    }
  }

  // Returns a GTransform that effectfully maps the error parameter.
  def mapErrorZIO[C, E1, E2](f: E1 => zio.UIO[E2]): GTransform[C, E1, C, E2] = new GTransform[C, E1, C, E2] {
    def effect[A, B](io: (A, C) => zio.ZIO[Any, E1, B]): (A, C) => zio.ZIO[Any, E2, B]                       = { (a: A, context: C) =>
      io(a, context).flatMapError(f)
    }
    def stream[A, B](io: (A, C) => zio.stream.ZStream[Any, E1, B]): (A, C) => zio.stream.ZStream[Any, E2, B] = {
      (a: A, context: C) =>
        io(a, context).catchAll(e => ZStream.fromZIO(f(e).flatMap(ZIO.fail(_))))
    }
  }
}

object ZTransform {
  def apply[ContextIn, ContextOut](
      f: ContextOut => ZIO[Any, StatusException, ContextIn]
  ): ZTransform[ContextIn, ContextOut] = GTransform(f)
}
