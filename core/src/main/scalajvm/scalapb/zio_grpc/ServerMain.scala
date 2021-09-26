package scalapb.zio_grpc
import zio._
import zio.Console._
import io.grpc.ServerBuilder
import io.grpc.protobuf.services.ProtoReflectionService

/** Quick-start server app. */
trait ServerMain extends zio.ZIOApp {
  def port: Int = 9000

  def welcome: ZIO[ZEnv, Throwable, Unit] =
    printLine("Server is running. Press Ctrl-C to stop.")

  // Override this to add services. For example
  // def serviceList =
  //    ServiceList.add(MyService)
  def services: ServiceList[ZEnv]

  def builder = ServerBuilder.forPort(port).addService(ProtoReflectionService.newInstance())

  def serverLive: ZLayer[ZEnv, Throwable, Server] = ServerLayer.fromServiceList(builder, services)

  val myAppLogic = welcome *> serverLive.build.useForever

  def run(args: List[String]) = myAppLogic.exitCode
}
