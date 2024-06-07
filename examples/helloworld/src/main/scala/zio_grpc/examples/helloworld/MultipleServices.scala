package zio_grpc.examples.helloworld

import io.grpc.StatusException
import io.grpc.protobuf.services.ProtoReflectionService;
import scalapb.zio_grpc.Server
import scalapb.zio_grpc.ServerMain
import scalapb.zio_grpc.ServiceList
import zio._
import zio.Console._

import io.grpc.examples.helloworld.helloworld.ZioHelloworld.{Greeter, Welcomer}
import io.grpc.examples.helloworld.helloworld.{HelloReply, HelloRequest}
import zio_grpc.examples.helloworld.userdatabase.UserDatabase
import scalapb.zio_grpc.ServerLayer

class WelcomerWithDatabase(database: UserDatabase) extends Welcomer {
  def welcome(
      request: HelloRequest
  ): ZIO[Any, StatusException, HelloReply] =
    database.fetchUser(request.name).map { user =>
      HelloReply(s"Welcome ${user.name}")
    }
}

object WelcomerWithDatabase {
  val layer: ZLayer[UserDatabase, Nothing, Welcomer] =
    ZLayer.fromFunction(new WelcomerWithDatabase(_))
}

object MultipleServices extends zio.ZIOAppDefault {
  val serverLayer = ServerLayer.fromServiceList(
    io.grpc.ServerBuilder
      .forPort(9090)
      .addService(ProtoReflectionService.newInstance()),
    ServiceList
      .addFromEnvironment[Welcomer]
      .addFromEnvironment[Greeter]
  )

  val ourApp = ZLayer.make[Server](
    serverLayer,
    UserDatabase.layer,
    WelcomerWithDatabase.layer,
    GreeterWithDatabase.layer
  )

  def run =
    (ourApp.build *> ZIO.never).exitCode
}
