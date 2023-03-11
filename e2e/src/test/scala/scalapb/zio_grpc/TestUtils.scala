package scalapb.zio_grpc

import zio.test.Assertion._
import io.grpc.{Metadata, Status, StatusRuntimeException}
import io.grpc.Status.Code

object TestUtils {
  def hasStatusCode(c: Status) =
    hasField[StatusRuntimeException, Code]("code", _.getStatus().getCode, equalTo(c.getCode))

  def hasDescription(d: String) =
    hasField[StatusRuntimeException, String](
      "description",
      e => Option(e.getStatus().getDescription()).getOrElse("GotNull"),
      equalTo(d)
    )

  def hasTrailerValue[T](key: Metadata.Key[T], value: T) =
    hasField[StatusRuntimeException, T]("trailers", e => Status.trailersFromThrowable(e).get(key), equalTo(value))
}
