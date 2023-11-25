package examples

import examples.greeter.ZioGreeter.{Greeter, ZGreeter}
import examples.greeter._
import zio._
import zio.stream.Stream
import io.grpc.Metadata
import io.grpc.ServerBuilder
import zio.Console._
import io.grpc.{Status, StatusException}
import scalapb.zio_grpc.Server
import scalapb.zio_grpc.SafeMetadata
import scalapb.zio_grpc.RequestContext
import scalapb.zio_grpc.ServerLayer

case class User(name: String)

// UserRepo service, used to resolve the user id that is provided to us
// by looking into a user database.
trait UserRepo {
  def findUser(name: String): ZIO[Any, StatusException, User]
}

// This is our "real" implementation of the service.
case class UserRepoImpl() extends UserRepo {
  def findUser(name: String): ZIO[Any, StatusException, User] = name match {
    case "john" => ZIO.succeed(User("John"))
    case _ =>
      ZIO.fail(Status.UNAUTHENTICATED.withDescription("No access!").asException)
  }
}

object UserRepo {
  val layer = ZLayer.succeed(UserRepoImpl())
}

// GreetingsRepo is a service that returns an appropriate greeting to
// any given user.
trait GreetingsRepo {
  def greetingForUser(user: User): ZIO[Any, StatusException, String]
}

// An implementation of the service.
case class GreetingsRepoImpl() extends GreetingsRepo {
  def greetingForUser(user: User): ZIO[Any, StatusException, String] =
    ZIO.succeed("Hello ${user.name}")
}

object GreetingsRepo {
  val layer = ZLayer.succeed(GreetingsRepoImpl())
}

object GreeterServiceWithMetadata {

  // Each request gets a User as a context parameter. The service itself
  // depends on a GreetingsRepo service.
  case class GreeterImpl(greetingsRepo: GreetingsRepo) extends ZGreeter[User] {
    def greet(req: Request, user: User): ZIO[Any, StatusException, Response] =
      for {
        greeting <- greetingsRepo.greetingForUser(user)
      } yield Response(s"${greeting}, req: ${req}")

    def points(request: Request, user: User): Stream[StatusException, Point] =
      ???

    def bidi(
        request: Stream[StatusException, Point],
        user: User
    ): Stream[StatusException, Response] = ???
  }

  val UserKey =
    Metadata.Key.of("user-key", io.grpc.Metadata.ASCII_STRING_MARSHALLER)

  // Fetches the user-key from the request's metadata and looks it up in the UserRepo.
  // We trust here that the user is who they claim to be...
  def findUser(
      userRepo: UserRepo,
      rc: RequestContext
  ): IO[StatusException, User] =
    for {
      name <- rc.metadata
        .get(UserKey)
        .someOrFail(
          Status.UNAUTHENTICATED
            .withDescription("No user-key header provided")
            .asException
        )
      user <- userRepo.findUser(name)
    } yield user

  val layer
      : ZLayer[UserRepo with GreetingsRepo, Nothing, ZGreeter[RequestContext]] =
    ZLayer.fromFunction((userRepo: UserRepo, greetingsRepo: GreetingsRepo) =>
      GreeterImpl(greetingsRepo).transformContextZIO(findUser(userRepo, _)).transform(LoggingTransform)
    )
}

object ExampleServerWithMetadata extends ZIOAppDefault {

  def serverWait: ZIO[Any, Throwable, Unit] =
    for {
      _ <- printLine("Server is running. Press Ctrl-C to stop.")
      _ <- (print(".") *> ZIO.sleep(1.second)).forever
    } yield ()

  def serverLive(
      port: Int
  ): ZLayer[UserRepo with GreetingsRepo, Throwable, Server] =
    ServerLayer.fromServiceLayer(ServerBuilder.forPort(port))(
      GreeterServiceWithMetadata.layer
    )

  val myAppLogic =
    serverWait.provideLayer(
      (UserRepo.layer ++ GreetingsRepo.layer) >>>
        serverLive(8080)
    )

  def run = myAppLogic.exitCode
}
