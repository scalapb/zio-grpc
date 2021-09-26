package scalapb.zio_grpc

import zio.{Task, ZIO}
import io.grpc.Status

object GIO {
  def fromTask[A](task: Task[A]) =
    task.mapError(e => Status.INTERNAL.withDescription(e.getMessage).withCause(e))

  @deprecated("use attempt", "0.6.0")
  def effect[A](effect: => A) = attempt(effect)

  def attempt[A](effect: => A) = fromTask(Task.attempt(effect))
}
