package zio_grpc.examples.helloworld

import io.grpc.examples.helloworld.helloworld.ZioHelloworld.GreeterClient
import io.grpc.examples.helloworld.helloworld.HelloRequest
import io.grpc.{
  CallOptions,
  ManagedChannelBuilder,
  Metadata,
  MethodDescriptor,
  StatusException
}
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
      _ <- metadata.put(UserKey, user.name)
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
  val clientLayer = GreeterClient.live(channel)

  def appLogic1: ZIO[GreeterClient, StatusException, Unit] =
    for {
      // With client accessor
      r1 <-
        GreeterClient
          .withMetadataZIO(userToMetadata(User("user1")))
          .sayHello(HelloRequest("World"))
      _ <- printLine(r1.message).orDie
    } yield ()

  // Option 2: through a managed client

  // The metadata is fixed for the client, but can be overriden by
  // `withMetadataZIO`, or mapped with `mapMetadataZIO` - see below.
  val userClientManaged: ZIO[Scope, Throwable, GreeterClient] =
    GreeterClient.scoped(channel, metadata = userToMetadata(User("user1")))

  def appLogic2 =
    ZIO.scoped {
      userClientManaged.flatMap { client =>
        for {
          r1 <-
            client
              .sayHello(HelloRequest("World"))
          _ <- printLine(r1.message)
          r2 <-
            client
              .withMetadataZIO(userToMetadata(User("user2")))
              .sayHello(HelloRequest("World"))
          _ <- printLine(r2.message)
        } yield ()
      }
    }

  final def run =
    (
      appLogic1.provideLayer(clientLayer) *>
        appLogic2
    ).exitCode
}
