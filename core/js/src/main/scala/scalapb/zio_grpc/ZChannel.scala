package scalapb.zio_grpc

import io.grpc.Channel

class ZChannel[-R](val channel: Channel) {
  def client = channel.client
}
