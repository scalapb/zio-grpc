package scalapb.zio_grpc

import zio.{Has, Tag}

sealed trait Combinable[R, C] {
  def union(r: R, c: C): R with C
}

object Combinable {
  implicit def anyAny: Combinable[Any, Any]                            =
    new Combinable[Any, Any] {
      def union(r: Any, c: Any): Any = r
    }
  implicit def anyHas[C <: Has[_]]: Combinable[Any, C]                 =
    new Combinable[Any, C] {
      def union(r: Any, c: C): C = c
    }
  implicit def hasAny[R <: Has[_]]: Combinable[R, Any]                 =
    new Combinable[R, Any] {
      def union(r: R, c: Any): R = r
    }
  implicit def hasHas[R <: Has[_], C <: Has[_]: Tag]: Combinable[R, C] =
    new Combinable[R, C] {
      def union(r: R, c: C): R with C = r.union[C](c)
    }
}
