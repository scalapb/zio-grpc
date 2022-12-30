package scalapb.zio_grpc

import zio.test._
import zio._
import testservice.ZioTestservice.{TestServiceClient, TestServiceClientWithMetadata}
import testservice.ZioTestservice.ZTestService
import testservice._
import io.grpc.ServerBuilder
import io.grpc.Metadata
import io.grpc.ManagedChannelBuilder
import io.grpc.Status
import zio.console.Console
import zio.stream.ZStream
import zio.clock.Clock

object EnvSpec extends DefaultRunnableSpec with MetadataTests {
  case class User(name: String)

  case class Context(user: User, response: SafeMetadata)

  val getUser             = ZIO.access[Has[Context]](_.get.user)
  val getResponseMetadata = ZIO.access[Has[Context]](_.get.response)

  object ServiceWithConsole extends ZTestService[Console with Clock, Has[Context]] {
    def unary(request: Request): ZIO[Console with Has[Context], Status, Response] =
      for {
        user <- getUser
        md   <- getResponseMetadata
        _    <- md.put(RequestIdKey, "1")
        _    <- ZIO.fail(Status.FAILED_PRECONDITION).when(user.name == "Eve")
      } yield Response(out = user.name)

    def serverStreaming(
        request: Request
    ): ZStream[Console with Has[Context], Status, Response] =
      ZStream.accessStream { (u: Has[Context]) =>
        ZStream
          .fromEffect(
            u.get.response
              .put(RequestIdKey, "1")
              .andThen(ZIO.fail(Status.FAILED_PRECONDITION).when(u.get.user.name == "Eve"))
          )
          .drain ++
          ZStream(
            Response(u.get.user.name),
            Response(u.get.user.name)
          )
      }

    def clientStreaming(
        request: zio.stream.ZStream[Any, Status, Request]
    ): ZIO[Has[Context], Status, Response] =
      for {
        user <- getUser
        md   <- getResponseMetadata
        _    <- md.put(RequestIdKey, "1")
        _    <- ZIO.fail(Status.FAILED_PRECONDITION).when(user.name == "Eve")
      } yield Response(user.name)

    def bidiStreaming(
        request: zio.stream.ZStream[Any, Status, Request]
    ): ZStream[Has[Context], Status, Response] =
      ZStream.accessStream { (u: Has[Context]) =>
        ZStream
          .fromEffect(
            u.get.response
              .put(RequestIdKey, "1")
              .andThen(ZIO.fail(Status.FAILED_PRECONDITION).when(u.get.user.name == "Eve"))
          )
          .drain ++ ZStream(Response(u.get.user.name))
      }
  }

  val UserKey =
    Metadata.Key.of("user-key", io.grpc.Metadata.ASCII_STRING_MARSHALLER)

  def parseUser(rc: RequestContext): IO[Status, Context] =
    rc.metadata.get(UserKey).flatMap {
      case Some("alice") => IO.fail(Status.PERMISSION_DENIED.withDescription("You are not allowed!"))
      case Some(name)    => IO.succeed(Context(User(name), rc.responseMetadata))
      case None          => IO.fail(Status.UNAUTHENTICATED)
    }

  val serviceLayer = ServiceWithConsole.transformContextM(parseUser(_)).toLayer

  val serverLayer: ZLayer[Has[ZTestService[Any, Has[RequestContext]]], Throwable, Server] =
    ServerLayer.access[ZTestService[Any, Has[RequestContext]]](ServerBuilder.forPort(0))

  override def clientLayer(
      userName: Option[String]
  ): ZLayer[Server, Nothing, TestServiceClient] =
    ZLayer.fromServiceManaged { (ss: Server.Service) =>
      ZManaged.fromEffect(ss.port).orDie >>= { (port: Int) =>
        val ch = ZManagedChannel(
          ManagedChannelBuilder.forAddress("localhost", port).usePlaintext(),
          Seq(
            ZClientInterceptor.headersUpdater((_, _, md) => ZIO.foreach(userName)(un => md.put(UserKey, un)).unit)
          )
        )
        TestServiceClient
          .managed(ch)
          .orDie
      }
    }

  override def clientMetadataLayer: ZLayer[Server, Nothing, TestServiceClientWithMetadata] =
    ZLayer.fromServiceManaged { (ss: Server.Service) =>
      ZManaged.fromEffect(ss.port).orDie >>= { (port: Int) =>
        val ch = ZManagedChannel(
          ManagedChannelBuilder.forAddress("localhost", port).usePlaintext(),
          Seq(
            ZClientInterceptor.headersUpdater((_, _, md) => md.put(UserKey, "bob").unit)
          )
        )
        TestServiceClientWithMetadata
          .managed(ch)
          .orDie
      }
    }

  val layers = serviceLayer >>> (serverLayer ++ Annotations.live)

  def spec =
    suite("EnvSpec")(
      specs
    ).provideCustomLayer(layers.orDie)
}
