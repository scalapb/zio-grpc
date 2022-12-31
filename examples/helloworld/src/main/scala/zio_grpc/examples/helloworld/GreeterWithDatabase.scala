package zio_grpc.examples.helloworld

import io.grpc.Status
import scalapb.zio_grpc.Server
import scalapb.zio_grpc.ServerMain
import scalapb.zio_grpc.ServiceList
import zio._
import zio.Console._

import io.grpc.examples.helloworld.helloworld.ZioHelloworld.Greeter
import io.grpc.examples.helloworld.helloworld.{HelloReply, HelloRequest}
import zio_grpc.examples.helloworld.userdatabase.UserDatabase
import scalapb.zio_grpc.ServerLayer

package object userdatabase {
  trait UserDatabase {
    def fetchUser(name: String): IO[Status, User]
  }

  object UserDatabase {
    val layer = ZLayer.succeed(new UserDatabase {
      def fetchUser(name: String): IO[Status, User] =
        ZIO.succeed(User(name))
    })
  }
}

class GreeterWithDatabase(database: UserDatabase) extends Greeter {
  def sayHello(
      request: HelloRequest
  ): ZIO[Any, Status, HelloReply] =
    database.fetchUser(request.name).map { user =>
      HelloReply(s"Hello ${user.name}")
    }
}

object GreeterWithDatabase {
  val layer: ZLayer[UserDatabase, Nothing, Greeter] = ZLayer.fromFunction(new GreeterWithDatabase(_))
}

object GreeterWithDatabaseServer extends zio.ZIOAppDefault {
  val serverLayer = ServerLayer.fromServiceLayer(
    io.grpc.ServerBuilder.forPort(9090)
  )(GreeterWithDatabase.layer)

  val ourApp = UserDatabase.layer >>> serverLayer

  def run =
    (ourApp.build *> ZIO.never).exitCode
}