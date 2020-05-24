package scalapb.zio_grpc

import zio.test._
import zio.test.Assertion._
import zio._
import zio.stream.Stream
import testservice.ZioTestservice.TestServiceClient
import testservice.ZioTestservice.TestService
import testservice._
import io.grpc.ServerBuilder
import io.grpc.Metadata
import io.grpc.ManagedChannelBuilder
import io.grpc.Status
import TestUtils._

trait MetadataTests {
  def clientLayer(
      userName: Option[String]
  ): ZLayer[Server, Nothing, TestServiceClient]

  val authClient = clientLayer(Some("bob"))
  val unauthClient = clientLayer(Some("alice"))
  val unsetClient = clientLayer(None)

  val permissionDenied = fails(hasStatusCode(Status.PERMISSION_DENIED))
  val unauthenticated = fails(hasStatusCode(Status.UNAUTHENTICATED))

  val unaryEffect = TestServiceClient.unary(Request())
  val serverStreamingEffect =
    TestServiceClient.serverStreaming(Request()).runCollect
  val clientStreamingEffect = TestServiceClient.clientStreaming(Stream.empty)
  val bidiEffect = TestServiceClient.bidiStreaming(Stream.empty).runCollect

  def permissionDeniedSuite =
    suite("unauthorized request fail for")(
      testM("unary") {
        assertM(unaryEffect.run)(permissionDenied)
      },
      testM("server streaming") {
        assertM(serverStreamingEffect.run)(permissionDenied)
      },
      testM("client streaming") {
        assertM(clientStreamingEffect.run)(permissionDenied)
      },
      testM("bidi streaming") {
        assertM(bidiEffect.run)(permissionDenied)
      }
    ).provideLayer(unauthClient)

  def unauthenticatedSuite =
    suite("authorized request fail for")(
      testM("unary") {
        assertM(unaryEffect.run)(unauthenticated)
      },
      testM("server streaming") {
        assertM(serverStreamingEffect.run)(unauthenticated)
      },
      testM("client streaming") {
        assertM(clientStreamingEffect.run)(unauthenticated)
      },
      testM("bidi streaming") {
        assertM(bidiEffect.run)(unauthenticated)
      }
    ).provideLayer(unsetClient)

  def authenticatedSuite =
    suite("authorized request fail for")(
      testM("unary") {
        assertM(unaryEffect)(equalTo(Response("bob")))
      },
      testM("server streaming") {
        assertM(serverStreamingEffect)(
          equalTo(Seq(Response("bob"), Response("bob")))
        )
      },
      testM("client streaming") {
        assertM(clientStreamingEffect)(equalTo(Response("bob")))
      },
      testM("bidi streaming") {
        assertM(bidiEffect)(equalTo(Seq(Response("bob"))))
      }
    ).provideLayer(authClient)

  val specs = Seq(
    permissionDeniedSuite,
    unauthenticatedSuite,
    authenticatedSuite
  )
}

object ContextSpec extends DefaultRunnableSpec with MetadataTests {
  object ServiceWithContext extends TestService.WithContext[String] {
    def unary(request: Request, context: String): IO[io.grpc.Status, Response] =
      IO.succeed(Response(context))
    def serverStreaming(
        request: Request,
        context: String
    ): Stream[io.grpc.Status, Response] =
      Stream(Response(context), Response(context))
    def clientStreaming(
        request: Stream[Status, Request],
        context: String
    ): IO[Status, Response] = IO.succeed(Response(context))
    def bidiStreaming(
        request: Stream[Status, Request],
        context: String
    ): Stream[Status, Response] = Stream(Response(context))
  }

  val UserKey =
    Metadata.Key.of("user-key", io.grpc.Metadata.ASCII_STRING_MARSHALLER)

  val serviceLayer = ZLayer.succeed(
    TestService.transformContext(
      ServiceWithContext,
      (m: Metadata) =>
        Option(m.get(UserKey)) match {
          case Some("alice") =>
            IO.fail(
              Status.PERMISSION_DENIED.withDescription("You are not allowed!")
            )
          case Some(name) => IO.succeed(name)
          case None       => IO.fail(Status.UNAUTHENTICATED)
        }
    )
  )

  val serverLayer
      : ZLayer[Has[TestService.WithContext[Metadata]], Nothing, Server] =
    Server.live[TestService.WithContext[Metadata]](ServerBuilder.forPort(0))

  def clientLayer(
      userName: Option[String]
  ): ZLayer[Server, Nothing, TestServiceClient] =
    ZLayer.fromServiceManaged { ss: Server.Service =>
      ZManaged.fromEffect(ss.port).orDie >>= { port: Int =>
        val ch = ZManagedChannel(
          ManagedChannelBuilder.forAddress("localhost", port).usePlaintext()
        )
        TestServiceClient
          .managed(
            ch,
            headers = {
              val md = new Metadata()
              userName.foreach(md.put(UserKey, _))
              md
            }
          )
          .orDie
      }
    }

  val layers = serviceLayer >>> (serverLayer ++ Annotations.live)

  def spec =
    suite("ContextSpec")(specs: _*).provideLayer(layers)
}
