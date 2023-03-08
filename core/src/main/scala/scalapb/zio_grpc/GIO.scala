package scalapb.zio_grpc

import io.grpc.{Status, StatusRuntimeException}
import zio.{Task, ZIO, IO}

object GIO {
  def fromTask[A](task: Task[A]): IO[StatusRuntimeException, A] =
    task.mapError(e => Status.INTERNAL.withDescription(e.getMessage).withCause(e).asRuntimeException())

  @deprecated("use attempt", "0.6.0")
  def effect[A](effect: => A): IO[StatusRuntimeException, A] = attempt(effect)

  def attempt[A](effect: => A): IO[StatusRuntimeException, A] = fromTask(ZIO.attempt(effect))
}
