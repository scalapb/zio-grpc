package scalapb.zio_grpc

import zio.test.Assertion._
import io.grpc.{Metadata, Status, StatusException}
import io.grpc.Status.Code
import zio.test.Assertion
import zio.test.AssertionM.Render.param

import scala.collection.JavaConverters

object TestUtils {
  def hasStatusCode(c: Status) =
    hasField[Status, Code]("code", _.getCode, equalTo(c.getCode))

  def hasDescription(d: String) =
    hasField[Status, String]("description", d => Option(d.getDescription()).getOrElse("GotNull"), equalTo(d))

  def hasTrailerValue[T](key: Metadata.Key[T], value: T) =
    hasField[Status, T]("trailers", s => Status.trailersFromThrowable(s.getCause).get(key), equalTo(value))
}
