package scalapb.zio_grpc

import scalapb.TypeMapper
import scalapb.zio_grpc.testservice.ResponseTypeMapped

case class WrappedString(value: String)

object WrappedString {
  implicit val tm: TypeMapper[scalapb.zio_grpc.testservice.ResponseTypeMapped, WrappedString] =
    TypeMapper[scalapb.zio_grpc.testservice.ResponseTypeMapped, WrappedString](v => WrappedString(v.out))(v =>
      ResponseTypeMapped(v.value)
    )
}
