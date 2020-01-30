package scalapb.grpc.zio

import zio.Task
import io.grpc.Status

object GIO {
  def fromTask[A](task: Task[A]) =
    task.mapError(
      e => Status.INTERNAL.withDescription(e.getMessage).withCause(e)
    )

  def effect[A](effect: => A) = fromTask(Task.effect(effect))
}
