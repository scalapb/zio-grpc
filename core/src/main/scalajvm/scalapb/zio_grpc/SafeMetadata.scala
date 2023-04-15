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
    wrapZIO(metadata => ZIO.succeed(f(metadata)))

  def wrapZIO[R, E, A](f: Metadata => ZIO[R, E, A]): ZIO[R, E, A] =
    sem.withPermit(f(metadata))
}

object SafeMetadata {
  def make: UIO[SafeMetadata] = fromMetadata(new Metadata)

  def make(pairs: (String, String)*): UIO[SafeMetadata] = {
    val md = new Metadata
    pairs.foreach { case (key, value) =>
      md.put(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER), value)
    }
    SafeMetadata.fromMetadata(md)
  }

  /** Creates a new SafeMetadata by taking ownership of the given metadata. The provided metadata should not be used
    * after calling this method.
    */
  def fromMetadata(metadata: => Metadata): UIO[SafeMetadata] =
    Semaphore.make(1).map(s => new SafeMetadata(s, metadata))
}
