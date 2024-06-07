package scalapb.zio_grpc

import zio.test.Assertion._
import io.grpc.{Status, StatusException}
import io.grpc.Status.Code
import zio.URIO
import zio.stream.ZStream
import zio.test.Assertion

object TestUtils {
  def hasStatusCode(c: Status) =
    hasField[StatusException, Code]("code", _.getStatus().getCode(), equalTo(c.getCode()))

  def hasDescription(d: String) =
    hasField[StatusException, String](
      "description",
      e => Option(e.getStatus().getDescription()).getOrElse("GotNull"),
      equalTo(d)
    )

  def collectWithError[R, E, A](
      zs: ZStream[R, E, A]
  ): URIO[R, (List[A], Option[E])] =
    zs.either
      .runFold((List.empty[A], Option.empty[E])) {
        case ((l, _), Left(e))  => (l, Some(e))
        case ((l, e), Right(a)) => (a :: l, e)
      }
      .map { case (la, oe) => (la.reverse, oe) }

  def tuple[A, B](
      assertionA: Assertion[A],
      assertionB: Assertion[B]
  ): Assertion[(A, B)] =
    hasField[(A, B), A]("", _._1, assertionA) &&
      hasField[(A, B), B]("", _._2, assertionB)
}
