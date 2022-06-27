package scalapb.zio_grpc

trait ServiceModule {
  type Service[R]

  def clientService(
      channel: io.grpc.Channel,
      options: io.grpc.CallOptions = io.grpc.CallOptions.DEFAULT,
      headers: => io.grpc.Metadata = new io.grpc.Metadata()
  ): Service[Any]
}
