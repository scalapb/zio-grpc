package scalapb.zio_grpc

import io.grpc.{Status, StatusException}
import zio.{Task, ZIO}

object GIO {
  def fromTask[A](task: Task[A]) =
    task.mapError(e => Status.INTERNAL.withDescription(e.getMessage).withCause(e).asException())

  @deprecated("use attempt", "0.6.0")
  def effect[A](effect: => A) = attempt(effect)

  def attempt[A](effect: => A) = fromTask(ZIO.attempt(effect))
}
