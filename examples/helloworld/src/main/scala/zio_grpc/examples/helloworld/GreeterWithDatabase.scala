package zio_grpc.examples.helloworld

import io.grpc.Status
import scalapb.zio_grpc.ServerMain
import scalapb.zio_grpc.ServiceList
import zio._
import zio.Console._

import io.grpc.examples.helloworld.helloworld.ZioHelloworld.RGreeter
import io.grpc.examples.helloworld.helloworld.{HelloReply, HelloRequest}
import zio_grpc.examples.helloworld.userdatabase.UserDatabase
import scalapb.zio_grpc.ServerLayer

package object userdatabase {
  trait UserDatabase {
    def fetchUser(name: String): IO[Status, User]
  }

  object UserDatabase {
    // accessor
    def fetchUser(name: String): ZIO[UserDatabase, Status, User] =
      ZIO.environmentWithZIO[UserDatabase](_.get.fetchUser(name))

    val live = zio.ZLayer.succeed(new UserDatabase {
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

object GreeterWithDatabaseServer extends zio.ZIOAppDefault {
  val serverLayer = ServerLayer.fromServiceLayer(
    io.grpc.ServerBuilder.forPort(9090)
  )(GreeterWithDatabase.toLayer)

  val ourApp = (UserDatabase.live ++ Console.any) >>> serverLayer

  def run: zio.URIO[zio.ZEnv, ExitCode] =
    ourApp.build.useForever.exitCode
}
