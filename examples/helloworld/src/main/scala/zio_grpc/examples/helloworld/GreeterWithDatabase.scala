package zio_grpc.examples.helloworld

import io.grpc.Status
import scalapb.zio_grpc.ServerMain
import scalapb.zio_grpc.ServiceList
import zio.{ZEnv, ZIO}
import zio.console._

import io.grpc.examples.helloworld.helloworld.ZioHelloworld.RGreeter
import io.grpc.examples.helloworld.helloworld.{HelloReply, HelloRequest}
import zio_grpc.examples.helloworld.userdatabase.UserDatabase
import scalapb.zio_grpc.ServerLayer
import zio.ExitCode
import zio.Has
import zio.IO

package object userdatabase {
  type UserDatabase = Has[UserDatabase.Service]

  object UserDatabase {
    trait Service {
      def fetchUser(name: String): IO[Status, User]
    }

    // accessor
    def fetchUser(name: String): ZIO[UserDatabase, Status, User] =
      ZIO.accessM[UserDatabase](_.get.fetchUser(name))

    val live = zio.ZLayer.succeed(new Service {
      def fetchUser(name: String): IO[Status, User] =
        IO.succeed(User(name))
    })
  }
}

object GreeterWithDatabase extends RGreeter[UserDatabase with Console] {
  def sayHello(
      request: HelloRequest
  ): ZIO[UserDatabase with Console, Status, HelloReply] =
    UserDatabase.fetchUser(request.name).map { user =>
      HelloReply(s"Hello ${user.name}")
    }
}

object GreeterWithDatabaseServer extends zio.App {
  val serverLayer = ServerLayer.fromServiceLayer(
    io.grpc.ServerBuilder.forPort(9090)
  )(GreeterWithDatabase.toLayer)

  val ourApp = (UserDatabase.live ++ Console.any) >>> serverLayer

  def run(args: List[String]): zio.URIO[zio.ZEnv, ExitCode] =
    ourApp.build.useForever.exitCode
}
