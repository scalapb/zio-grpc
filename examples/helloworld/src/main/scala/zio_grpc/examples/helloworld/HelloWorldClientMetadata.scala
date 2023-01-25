package zio_grpc.examples.helloworld

import io.grpc.examples.helloworld.helloworld.ZioHelloworld.GreeterClient
import io.grpc.examples.helloworld.helloworld.HelloRequest
import io.grpc.{CallOptions, ManagedChannelBuilder, Metadata, MethodDescriptor, Status}
import zio.Console._
import scalapb.zio_grpc.{SafeMetadata, ZClientInterceptor, ZManagedChannel}
import zio._

case class User(name: String)

object HelloWorldClientMetadata extends zio.ZIOAppDefault {
  val UserKey =
    Metadata.Key.of("user-key", io.grpc.Metadata.ASCII_STRING_MARSHALLER)

  def userToMetadata(user: User): UIO[SafeMetadata] =
    for {
      metadata <- SafeMetadata.make
      _        <- metadata.put(UserKey, user.name)
    } yield metadata

  // An effect that fetches a User from the environment and transforms it to
  // Metadata
  def userEnvToMetadata: URIO[User, SafeMetadata] =
    ZIO.service[User].flatMap(userToMetadata)

  val channel =
    ZManagedChannel(
      ManagedChannelBuilder.forAddress("localhost", 9000).usePlaintext()
    )

  // Option 1: through layer and accessors
  val clientLayer = GreeterClient.live(channel, headers = userEnvToMetadata)

  type UserClient = GreeterClient.ZService[User]

  // The default accessors expect the client type that has no context. We need
  // to set up accessors for the User context
  object UserClient extends GreeterClient.Accessors[User]

  def appLogic1: ZIO[UserClient, Status, Unit] =
    for {
      // With provideSomeLayer:
      r1 <-
        UserClient
          .sayHello(HelloRequest("World"))
          .provideSomeLayer[UserClient](ZLayer.succeed(User("user1")))
      _  <- printLine(r1.message).orDie

      // With provideSomeEnvironment:
      r2 <-
        UserClient
          .sayHello(HelloRequest("World"))
          .provideSomeEnvironment(
            (_: ZEnvironment[UserClient]) ++ ZEnvironment(
              User("user1")
            )
          )
      _  <- printLine(r2.message).orDie
    } yield ()

  // Option 2: through a managed client
  val userClientManaged: ZIO[Scope, Throwable, GreeterClient.ZService[User]] =
    GreeterClient.scoped(channel, headers = userEnvToMetadata)

  def appLogic2 =
    ZIO.scoped {
      userClientManaged.flatMap { client =>
        for {
          r1 <-
            client
              .sayHello(HelloRequest("World"))
              .provideEnvironment(ZEnvironment(User("user1")))
          _  <- printLine(r1.message)
          r2 <-
            client
              .sayHello(HelloRequest("World"))
              .provideEnvironment(ZEnvironment(User("user2")))
          _  <- printLine(r2.message)
        } yield ()
      }
    }

  // Option 3: by changing the stub
  val clientManaged = GreeterClient.scoped(channel)
  def appLogic3     =
    ZIO.scoped {
      clientManaged.flatMap { client =>
        for {
          // Pass metadata effectfully
          r1 <-
            client
              .withMetadataZIO(userToMetadata(User("hello")))
              .sayHello(HelloRequest("World"))
          _  <- printLine(r1.message)
        } yield ()
      }
    }

  final def run =
    (
      appLogic1.provideLayer(clientLayer) *>
        appLogic2 *>
        appLogic3
    ).exitCode
}
