package scalapb.zio_grpc

import zio.test._
import zio._
import testservice.ZioTestservice.{TestServiceClient, TestServiceClientWithResponseMetadata}
import testservice.ZioTestservice.ZTestService
import testservice._
import io.grpc.ServerBuilder
import io.grpc.Metadata
import io.grpc.ManagedChannelBuilder
import io.grpc.{Status, StatusRuntimeException}
import zio.stream.ZStream

object EnvSpec extends ZIOSpecDefault with MetadataTests {
  case class User(name: String)

  case class Context(user: User, response: SafeMetadata)

  object ServiceWithConsole extends ZTestService[Context] {
    def unary(request: Request, context: Context): ZIO[Any, StatusRuntimeException, Response] =
      for {
        _ <- context.response.put(RequestIdKey, "1")
        _ <- ZIO.fail(Status.FAILED_PRECONDITION.asRuntimeException()).when(context.user.name == "Eve")
      } yield Response(context.user.name)

    def serverStreaming(
        request: Request,
        context: Context
    ): ZStream[Any, StatusRuntimeException, Response] =
      ZStream
        .fromZIO(
          for {
            _ <- context.response.put(RequestIdKey, "1")
            _ <- ZIO.fail(Status.FAILED_PRECONDITION.asRuntimeException()).when(context.user.name == "Eve")
          } yield ()
        )
        .drain ++
        ZStream(
          Response(context.user.name),
          Response(context.user.name)
        )

    def clientStreaming(
        request: ZStream[Any, StatusRuntimeException, Request],
        context: Context
    ): ZIO[Any, StatusRuntimeException, Response] =
      for {
        _ <- context.response.put(RequestIdKey, "1")
        _ <- ZIO.fail(Status.FAILED_PRECONDITION.asRuntimeException()).when(context.user.name == "Eve")
      } yield Response(context.user.name)

    def bidiStreaming(
        request: ZStream[Any, StatusRuntimeException, Request],
        context: Context
    ): ZStream[Any, StatusRuntimeException, Response] =
      ZStream
        .fromZIO(
          for {
            _ <- context.response.put(RequestIdKey, "1")
            _ <- ZIO.fail(Status.FAILED_PRECONDITION.asRuntimeException()).when(context.user.name == "Eve")
          } yield ()
        )
        .drain ++ ZStream(Response(context.user.name))
  }

  val UserKey =
    Metadata.Key.of("user-key", io.grpc.Metadata.ASCII_STRING_MARSHALLER)

  def parseUser(rc: RequestContext): IO[StatusRuntimeException, Context] =
    rc.metadata.get(UserKey).flatMap {
      case Some("alice") =>
        ZIO.fail(
          Status.PERMISSION_DENIED.withDescription("You are not allowed!").asRuntimeException()
        )
      case Some(name)    => ZIO.succeed(Context(User(name), rc.responseMetadata))
      case None          => ZIO.fail(Status.UNAUTHENTICATED.asRuntimeException())
    }

  val serviceLayer = ZLayer.succeed(ServiceWithConsole.transformContextZIO(parseUser(_)))

  val serverLayer: ZLayer[ZTestService[RequestContext], Throwable, Server] =
    ServerLayer.fromEnvironment[ZTestService[RequestContext]](ServerBuilder.forPort(0))

  override def clientLayer(
      userName: Option[String]
  ): URLayer[Server, TestServiceClient] =
    ZLayer.scoped {
      ZIO.environmentWithZIO { (ss: ZEnvironment[Server]) =>
        ss.get[Server].port.orDie flatMap { (port: Int) =>
          val ch = ZManagedChannel(
            ManagedChannelBuilder.forAddress("localhost", port).usePlaintext(),
            Seq(
              ZClientInterceptor.headersUpdater((_, _, md) => ZIO.foreach(userName)(un => md.put(UserKey, un)).unit)
            )
          )
          TestServiceClient
            .scoped(ch)
            .orDie
        }
      }
    }

  override def clientMetadataLayer: URLayer[Server, TestServiceClientWithResponseMetadata] =
    ZLayer.scoped {
      ZIO.environmentWithZIO { (ss: ZEnvironment[Server]) =>
        ss.get[Server].port.orDie flatMap { (port: Int) =>
          val ch = ZManagedChannel(
            ManagedChannelBuilder.forAddress("localhost", port).usePlaintext(),
            Seq(
              ZClientInterceptor.headersUpdater((_, _, md) => md.put(UserKey, "bob").unit)
            )
          )
          TestServiceClientWithResponseMetadata
            .scoped(ch)
            .orDie
        }
      }
    }

  val layers = serviceLayer >>> (serverLayer ++ Annotations.live)

  def spec =
    suite("EnvSpec")(
      specs
    ).provideLayer(layers.orDie)
}
