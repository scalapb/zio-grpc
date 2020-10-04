package zio_grpc.examples.helloworld

import io.grpc.examples.helloworld.helloworld.ZioHelloworld.GreeterClient
import io.grpc.examples.helloworld.helloworld.HelloRequest
import io.grpc.{
  ManagedChannelBuilder,
  Metadata,
  MethodDescriptor,
  CallOptions,
  Status
}
import zio.console._
import scalapb.zio_grpc.{SafeMetadata, ZClientInterceptor, ZManagedChannel}
import zio._

case class User(name: String)

object HelloWorldClientMetadata extends zio.App {
  val UserKey =
    Metadata.Key.of("user-key", io.grpc.Metadata.ASCII_STRING_MARSHALLER)

  def userToMetadata(user: User): UIO[SafeMetadata] =
    for {
      metadata <- SafeMetadata.make
      _ <- metadata.put(UserKey, user.name)
    } yield metadata

  // An effect that fetches a User from the environment and transforms it to
  // Metadata
  def userEnvToMetadata: URIO[Has[User], SafeMetadata] =
    ZIO.service[User].flatMap(userToMetadata)

  val channel =
    ZManagedChannel(
      ManagedChannelBuilder.forAddress("localhost", 9000).usePlaintext()
    )

  // Option 1: through layer and accessors
  val clientLayer = GreeterClient.live(channel, headers = userEnvToMetadata)

  type UserClient = Has[GreeterClient.ZService[Any, Has[User]]]

  // The default accessors expect the client type that has no context. We need
  // to set up accessors for the User context
  object UserClient extends GreeterClient.Accessors[Has[User]]

  def appLogic1: ZIO[UserClient with Console, Status, Unit] =
    for {
      // With provideSomeLayer:
      r1 <-
        UserClient
          .sayHello(HelloRequest("World"))
          .provideSomeLayer[UserClient](ZLayer.succeed(User("user1")))
      _ <- putStrLn(r1.message)

      // With provide:
      r2 <- UserClient.sayHello(HelloRequest("World")).provideSome {
        (ct: UserClient) => ct ++ Has(User("user1"))
      }
      _ <- putStrLn(r2.message)
    } yield ()

  // Option 2: through a managed client
  val userClientManaged
      : Managed[Throwable, GreeterClient.ZService[Any, Has[User]]] =
    GreeterClient.managed(channel, headers = userEnvToMetadata)

  def appLogic2 =
    userClientManaged.use { client =>
      for {
        r1 <- client.sayHello(HelloRequest("World")).provide(Has(User("user1")))
        _ <- putStrLn(r1.message)
        r2 <- client.sayHello(HelloRequest("World")).provide(Has(User("user2")))
        _ <- putStrLn(r2.message)
      } yield ()
    }

  // Option 3: by changing the stub
  val clientManaged = GreeterClient.managed(channel)
  def appLogic3 =
    clientManaged.use { client =>
      for {
        // Pass metadata effectfully
        r1 <-
          client
            .withMetadataM(userToMetadata(User("hello")))
            .sayHello(HelloRequest("World"))
        _ <- putStrLn(r1.message)

        // Pass prebuilt metadata:
        md <- SafeMetadata.make
        _ <- md.put(UserKey, "foo")
        r1 <- client.withMetadata(md).sayHello(HelloRequest("World"))
        _ <- putStrLn(r1.message)
      } yield ()
    }

  final def run(args: List[String]) =
    (
      appLogic1.provideCustomLayer(clientLayer) *>
        appLogic2 *>
        appLogic3
    ).exitCode
}
