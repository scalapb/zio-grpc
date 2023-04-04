package scalapb.zio_grpc

import zio.ZIO
import zio.UIO
import scalajs.js.Dictionary

final class SafeMetadata private (
    private[zio_grpc] val metadata: Dictionary[String]
) {}

object SafeMetadata {
  def make: UIO[SafeMetadata] = ZIO.succeed(new SafeMetadata(Dictionary.empty))

  def make(pairs: (String, String)*): UIO[SafeMetadata] =
    ZIO.succeed(new SafeMetadata(Dictionary(pairs: _*)))
}
