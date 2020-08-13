package scalapb.zio_grpc

import zio.test.Assertion._
import io.grpc.Status
import io.grpc.Status.Code

object TestUtils {
  def hasStatusCode(c: Status) =
    hasField[Status, Code]("code", _.getCode, equalTo(c.getCode))

  def hasDescription(d: String) =
    hasField[Status, String]("description", d => Option(d.getDescription()).getOrElse("GotNull"), equalTo(d))
}
