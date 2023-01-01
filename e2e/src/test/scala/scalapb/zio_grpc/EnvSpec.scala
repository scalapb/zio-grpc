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
import zio.stream.ZStream

object EnvSpec extends ZIOSpecDefault with MetadataTests {
  case class User(name: String)

  case class Context(user: User, response: SafeMetadata)

  val getUser             = ZIO.serviceWith[Context](_.user)
  val getResponseMetadata = ZIO.serviceWith[Context](_.response)

  object ServiceWithConsole extends ZTestService[Context] {
    def unary(request: Request): ZIO[Context, Status, Response] =
      for {
        user <- getUser
        md   <- getResponseMetadata
        _    <- md.put(RequestIdKey, "1")
      } yield Response(out = user.name)

    def serverStreaming(
        request: Request
    ): ZStream[Context, Status, Response] =
      ZStream.environmentWithStream { (u: ZEnvironment[Context]) =>
        ZStream
          .fromZIO(
            u.get.response.put(RequestIdKey, "1")
          )
          .drain ++
          ZStream(
            Response(u.get.user.name),
            Response(u.get.user.name)
          )
      }

    def clientStreaming(
        request: zio.stream.ZStream[Any, Status, Request]
    ): ZIO[Context, Status, Response] =
      for {
        n  <- getUser
        md <- getResponseMetadata
        _  <- md.put(RequestIdKey, "1")
      } yield Response(n.name)

    def bidiStreaming(
        request: zio.stream.ZStream[Any, Status, Request]
    ): ZStream[Context, Status, Response] =
      ZStream.environmentWithStream { (u: ZEnvironment[Context]) =>
        ZStream.fromZIO(u.get.response.put(RequestIdKey, "1")).drain ++ ZStream(Response(u.get.user.name))

      }
  }

  val UserKey =
    Metadata.Key.of("user-key", io.grpc.Metadata.ASCII_STRING_MARSHALLER)

  def parseUser(rc: RequestContext): IO[Status, Context] =
    rc.metadata.get(UserKey).flatMap {
      case Some("alice") =>
        ZIO.fail(
          Status.PERMISSION_DENIED.withDescription("You are not allowed!")
        )
      case Some(name)    => ZIO.succeed(Context(User(name), rc.responseMetadata))
      case None          => ZIO.fail(Status.UNAUTHENTICATED)
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

  override def clientMetadataLayer: URLayer[Server, TestServiceClientWithMetadata] =
    ZLayer.scoped {
      ZIO.environmentWithZIO { (ss: ZEnvironment[Server]) =>
        ss.get[Server].port.orDie flatMap { (port: Int) =>
          val ch = ZManagedChannel(
            ManagedChannelBuilder.forAddress("localhost", port).usePlaintext(),
            Seq(
              ZClientInterceptor.headersUpdater((_, _, md) => md.put(UserKey, "bob").unit)
            )
          )
          TestServiceClientWithMetadata
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
