package scalapb.zio_grpc

import zio.test._
import zio._
import testservice.ZioTestservice.TestServiceClient
import testservice.ZioTestservice.ZTestService
import testservice._
import io.grpc.ServerBuilder
import io.grpc.Metadata
import io.grpc.ManagedChannelBuilder
import io.grpc.Status
import zio.stream.ZStream

object EnvSpec extends ZIOSpecDefault with MetadataTests {
  case class User(name: String)

  val getUser = ZIO.environmentWith[User](_.get)

  object ServiceWithConsole extends ZTestService[Any, User] {
    def unary(request: Request): ZIO[User, Status, Response] =
      for {
        user <- getUser
      } yield Response(out = user.name)

    def serverStreaming(
        request: Request
    ): ZStream[User, Status, Response] =
      ZStream.environmentWithStream { (u: ZEnvironment[User]) =>
        ZStream(
          Response(u.get.name),
          Response(u.get.name)
        )
      }

    def clientStreaming(
        request: zio.stream.ZStream[Any, Status, Request]
    ): ZIO[User, Status, Response] = getUser.map(n => Response(n.name))

    def bidiStreaming(
        request: zio.stream.ZStream[Any, Status, Request]
    ): ZStream[User, Status, Response] =
      ZStream.environmentWithStream { (u: ZEnvironment[User]) =>
        ZStream(
          Response(u.get.name)
        )
      }
  }

  val UserKey =
    Metadata.Key.of("user-key", io.grpc.Metadata.ASCII_STRING_MARSHALLER)

  def parseUser(rc: RequestContext): IO[Status, User] =
    rc.metadata.get(UserKey).flatMap {
      case Some("alice") =>
        ZIO.fail(
          Status.PERMISSION_DENIED.withDescription("You are not allowed!")
        )
      case Some(name)    => ZIO.succeed(User(name))
      case None          => ZIO.fail(Status.UNAUTHENTICATED)
    }

  val serviceLayer = ServiceWithConsole.transformContextZIO(parseUser(_)).toLayer

  val serverLayer: ZLayer[ZTestService[Any, RequestContext], Throwable, Server] =
    ServerLayer.access[ZTestService[Any, RequestContext]](ServerBuilder.forPort(0))

  override def clientLayer(
      userName: Option[String]
  ): URLayer[Server, TestServiceClient] =
    ZLayer.scoped {
      ZIO.environmentWithZIO { (ss: ZEnvironment[Server.Service]) =>
        ss.get[Server.Service].port.orDie flatMap { (port: Int) =>
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

  val layers = serviceLayer >>> (serverLayer ++ Annotations.live)

  def spec =
    suite("EnvSpec")(
      specs: _*
    ).provideLayer(layers.orDie)
}
