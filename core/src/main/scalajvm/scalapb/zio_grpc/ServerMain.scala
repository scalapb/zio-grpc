package scalapb.zio_grpc
import zio._
import zio.Console._
import io.grpc.ServerBuilder
import io.grpc.protobuf.services.ProtoReflectionService

/** Quick-start server app. */
trait ServerMain extends zio.ZIOAppDefault {
  def port: Int = 9000

  def welcome: ZIO[Any, Throwable, Unit] =
    printLine(s"Server is running on port ${port}. Press Ctrl-C to stop.")

  // Override this to add services. For example
  // def serviceList =
  //    ServiceList.add(MyService)
  def services: ServiceList[Any]

  def builder = ServerBuilder.forPort(port).addService(ProtoReflectionService.newInstance())

  def serverLive: ZLayer[Any, Throwable, Server] = ServerLayer.fromServiceList(builder, services)

  val myAppLogic = welcome *> serverLive.launch

  def run = myAppLogic.exitCode
}
