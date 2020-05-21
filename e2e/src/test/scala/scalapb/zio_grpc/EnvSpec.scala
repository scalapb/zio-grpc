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
import zio.console.Console
import zio.stream.ZStream
import zio.clock.Clock

object EnvSpec extends DefaultRunnableSpec {
  case class User(name: String)

  val getUser = ZIO.access[Has[User]](_.get)

  object ServiceWithConsole
      extends ZTestService[Console with Clock, Has[User]] {
    def unary(request: Request): ZIO[Console with Has[User], Status, Response] =
      for {
        user <- getUser
      } yield Response(out = user.name)

    def serverStreaming(
        request: Request
    ): ZStream[Console with Has[User], Status, Response] =
      ZStream.accessStream { u: Has[User] =>
        ZStream(
          Response(u.get.name),
          Response(u.get.name)
        )
      }

    def clientStreaming(
        request: zio.stream.Stream[Status, Request]
    ): ZIO[Has[User], Status, Response] = getUser.map(n => Response(n.name))

    def bidiStreaming(
        request: zio.stream.Stream[Status, Request]
    ): ZStream[Has[User], Status, Response] =
      ZStream.accessStream { u: Has[User] =>
        ZStream(
          Response(u.get.name)
        )
      }
  }

  val UserKey =
    Metadata.Key.of("user-key", io.grpc.Metadata.ASCII_STRING_MARSHALLER)

  val RandomKey =
    Metadata.Key.of("random", io.grpc.Metadata.ASCII_STRING_MARSHALLER)

  def parseUser(rc: RequestContext): IO[Status, User] =
    Option(rc.metadata.get(UserKey)) match {
      case Some("alice") =>
        IO.fail(
          Status.PERMISSION_DENIED.withDescription("You are not allowed!")
        )
      case Some(name) => IO.succeed(User(name))
      case None       => IO.fail(Status.UNAUTHENTICATED)
    }

  val serviceLayer = ZTestService.toLayer(
    ZTestService.transformContext(ServiceWithConsole, parseUser)
  )

  val serverLayer
      : ZLayer[Has[ZTestService[Any, Has[RequestContext]]], Nothing, Server] =
    Server
      .live[ZTestService[Any, Has[RequestContext]]](ServerBuilder.forPort(0))

  def clientLayer(
      userName: Option[String]
  ): ZLayer[Server, Nothing, TestServiceClient[zio.random.Random]] =
    ZLayer.fromServiceManaged { ss: Server.Service =>
      ZManaged.fromEffect(ss.port).orDie >>= { port: Int =>
        val ch = ZManagedChannel(
          ManagedChannelBuilder.forAddress("localhost", port).usePlaintext()
        )
        TestServiceClient
          .managed(
            ch,
            headers = for {
              random <- zio.ZIO.access[zio.random.Random](_.get)
              rand <- random.nextInt
              md <- UIO.succeed({
                val md = new Metadata()
                userName.foreach(md.put(UserKey, _))
                md
              })
              _ <- UIO.succeed(md.put(RandomKey, s"$rand"))
            } yield md
          )
          .orDie
      }
    }

  val layers = serviceLayer >>> (serverLayer ++ Annotations.live)

  def spec =
    suite("EnvSpec")(
      ContextSpec.specs: _*
    ).provideLayer(layers)
}
