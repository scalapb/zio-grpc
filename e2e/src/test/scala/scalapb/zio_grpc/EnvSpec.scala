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
import zio.Console
import zio.stream.ZStream
import zio.Clock

object EnvSpec extends DefaultRunnableSpec with MetadataTests {
  case class User(name: String)

  val getUser = ZIO.access[Has[User]](_.get)

  object ServiceWithConsole extends ZTestService[Has[Console] with Has[Clock], Has[User]] {
    def unary(request: Request): ZIO[Has[Console] with Has[User], Status, Response] =
      for {
        user <- getUser
      } yield Response(out = user.name)

    def serverStreaming(
        request: Request
    ): ZStream[Has[Console] with Has[User], Status, Response] =
      ZStream.accessStream { (u: Has[User]) =>
        ZStream(
          Response(u.get.name),
          Response(u.get.name)
        )
      }

    def clientStreaming(
        request: zio.stream.ZStream[Any, Status, Request]
    ): ZIO[Has[User], Status, Response] = getUser.map(n => Response(n.name))

    def bidiStreaming(
        request: zio.stream.ZStream[Any, Status, Request]
    ): ZStream[Has[User], Status, Response] =
      ZStream.accessStream { (u: Has[User]) =>
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
        IO.fail(
          Status.PERMISSION_DENIED.withDescription("You are not allowed!")
        )
      case Some(name)    => IO.succeed(User(name))
      case None          => IO.fail(Status.UNAUTHENTICATED)
    }

  val serviceLayer = ServiceWithConsole.transformContextM(parseUser(_)).toLayer

  val serverLayer: ZLayer[Has[ZTestService[Any, Has[RequestContext]]], Throwable, Server] =
    ServerLayer.access[ZTestService[Any, Has[RequestContext]]](ServerBuilder.forPort(0))

  override def clientLayer(
      userName: Option[String]
  ): ZLayer[Server, Nothing, TestServiceClient] =
    ZLayer.fromServiceManaged { (ss: Server.Service) =>
      ZManaged.fromZIO(ss.port).orDie flatMap { (port: Int) =>
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

  val layers = serviceLayer >>> (serverLayer ++ Annotations.live)

  def spec =
    suite("EnvSpec")(
      specs: _*
    ).provideLayer(layers.orDie)
}
