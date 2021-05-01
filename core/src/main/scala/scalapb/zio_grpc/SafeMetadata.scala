package scalapb.zio_grpc

import zio.Semaphore
import io.grpc.Metadata
import zio.ZIO
import zio.UIO

final class SafeMetadata private (
    private val sem: Semaphore,
    private[zio_grpc] val metadata: Metadata
) {
  def get[T](key: Metadata.Key[T]): UIO[Option[T]] =
    wrap(m => Option(m.get(key)))

  def put[T](key: Metadata.Key[T], value: T): UIO[Unit] =
    wrap(_.put(key, value))

  def remove[T](key: Metadata.Key[T], value: T): UIO[Boolean] =
    wrap(_.remove(key, value))

  /** Creates an effect from a total side-effecting function of metadata */
  def wrap[A](f: Metadata => A): UIO[A] =
    sem.withPermit(ZIO.effectTotal(f(metadata)))
}

object SafeMetadata {
  def make: UIO[SafeMetadata] = fromMetadata(new Metadata)

  /** Creates a new SafeMetadata by taking ownership of the given metadata.
    * The provided metadata should not be used after calling this method.
    */
  def fromMetadata(metadata: => Metadata): UIO[SafeMetadata] =
    Semaphore.make(1).map(s => new SafeMetadata(s, metadata))
}
