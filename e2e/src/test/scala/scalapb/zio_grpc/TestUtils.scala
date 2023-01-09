package scalapb.zio_grpc

import zio.test.Assertion._
import io.grpc.{Metadata, Status, StatusException}
import io.grpc.Status.Code

object TestUtils {
  def hasStatusCode(c: Status) =
    hasField[StatusException, Code]("code", _.getStatus().getCode, equalTo(c.getCode))

  def hasDescription(d: String) =
    hasField[StatusException, String](
      "description",
      e => Option(e.getStatus().getDescription()).getOrElse("GotNull"),
      equalTo(d)
    )

  def hasTrailerValue[T](key: Metadata.Key[T], value: T) =
    hasField[StatusException, T]("trailers", e => Status.trailersFromThrowable(e).get(key), equalTo(value))
}
