package scalapb.zio_grpc

object TestServiceMain extends scalapb.zio_grpc.ServerMain {
  def services: ServiceList[Any] =
    ServiceList.addZIO(server.TestServiceImpl.makeFromEnv)
}
