package scalapb.zio_grpc

import io.grpc.{Status, StatusException}
import zio.{Task, ZIO, IO}

object GIO {
  def fromTask[A](task: Task[A]): IO[StatusException, A] =
    task.mapError(e => Status.INTERNAL.withDescription(e.getMessage).withCause(e).asException())

  @deprecated("use attempt", "0.6.0")
  def effect[A](effect: => A): IO[StatusException, A] = attempt(effect)

  def attempt[A](effect: => A): IO[StatusException, A] = fromTask(ZIO.attempt(effect))
}
