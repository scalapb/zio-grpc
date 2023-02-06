package scalapb.zio_grpc

import zio.test.{test, _}
import zio.test.Assertion._
import zio.stream.ZStream
import io.grpc.Metadata
import io.grpc.Status
import TestUtils._
import scalapb.zio_grpc.testservice._
import scalapb.zio_grpc.testservice.ZioTestservice._
import zio._

trait MetadataTests {
  def clientLayer(
      userName: Option[String]
  ): ZLayer[Server, Nothing, TestServiceClient]

  def clientMetadataLayer: ZLayer[Server, Nothing, TestServiceClientWithResponseMetadata]

  val RequestIdKey =
    Metadata.Key.of("request-id", io.grpc.Metadata.ASCII_STRING_MARSHALLER)

  val authClient   = clientLayer(Some("bob"))
  val unauthClient = clientLayer(Some("alice"))
  val unsetClient  = clientLayer(None)

  val permissionDenied = fails(hasStatusCode(Status.PERMISSION_DENIED))
  val unauthenticated  = fails(hasStatusCode(Status.UNAUTHENTICATED))

  val unaryEffect           = TestServiceClient.unary(Request())
  val serverStreamingEffect =
    TestServiceClient.serverStreaming(Request()).runCollect
  val clientStreamingEffect = TestServiceClient.clientStreaming(ZStream.empty)
  val bidiEffect            = TestServiceClient.bidiStreaming(ZStream.empty).runCollect

  val unaryEffectWithMd           =
    ZioTestservice.TestServiceClientWithResponseMetadata.unary(Request())
  val serverStreamingEffectWithMd =
    ZioTestservice.TestServiceClientWithResponseMetadata.serverStreaming(Request()).runCollect
  val clientStreamingEffectWithMd =
    ZioTestservice.TestServiceClientWithResponseMetadata.clientStreaming(ZStream.empty)
  val bidiEffectWithMd            =
    ZioTestservice.TestServiceClientWithResponseMetadata.bidiStreaming(ZStream.empty).runCollect

  val properTrailer = Assertion.assertion[Metadata]("trailers") { md =>
    md.containsKey(RequestIdKey) && md.get(RequestIdKey) == "1"
  }

  val properHeader = Assertion.assertion[Metadata]("headers") { md =>
    val keys = md.keys()
    keys.contains("content-type") && keys.contains("grpc-encoding") && keys.contains("grpc-accept-encoding")
  }

  def checkHeaderFrame(r: ResponseFrame[Response]) =
    assert(r.isInstanceOf[ResponseFrame.Headers])(isTrue) &&
      assert(r.asInstanceOf[ResponseFrame.Headers].headers)(properHeader)

  def checkTrailerFrame(r: ResponseFrame[Response]) =
    assert(r.isInstanceOf[ResponseFrame.Trailers])(isTrue) &&
      assert(r.asInstanceOf[ResponseFrame.Trailers].trailers)(properTrailer)

  def checkMessageFrame(r: ResponseFrame[Response]) =
    assert(r.isInstanceOf[ResponseFrame.Message[Response]])(isTrue) &&
      assert(r.asInstanceOf[ResponseFrame.Message[Response]].message)(equalTo(Response("bob")))

  def permissionDeniedSuite =
    suite("unauthorized request fail for")(
      test("unary") {
        assertZIO(unaryEffect.exit)(permissionDenied)
      },
      test("server streaming") {
        assertZIO(serverStreamingEffect.exit)(permissionDenied)
      },
      test("client streaming") {
        assertZIO(clientStreamingEffect.exit)(permissionDenied)
      },
      test("bidi streaming") {
        assertZIO(bidiEffect.exit)(permissionDenied)
      }
    ).provideLayer(unauthClient)

  def unauthenticatedSuite =
    suite("authorized request fail for")(
      test("unary") {
        assertZIO(unaryEffect.exit)(unauthenticated)
      },
      test("server streaming") {
        assertZIO(serverStreamingEffect.exit)(unauthenticated)
      },
      test("client streaming") {
        assertZIO(clientStreamingEffect.exit)(unauthenticated)
      },
      test("bidi streaming") {
        assertZIO(bidiEffect.exit)(unauthenticated)
      }
    ).provideLayer(unsetClient)

  def authenticatedSuite =
    suite("authorized request")(
      test("unary") {
        assertZIO(unaryEffect)(equalTo(Response("bob")))
      },
      test("server streaming") {
        assertZIO(serverStreamingEffect)(
          equalTo(Seq(Response("bob"), Response("bob")))
        )
      },
      test("client streaming") {
        assertZIO(clientStreamingEffect)(equalTo(Response("bob")))
      },
      test("bidi streaming") {
        assertZIO(bidiEffect)(equalTo(Seq(Response("bob"))))
      }
    ).provideLayer(authClient)

  def metadataSuite =
    suite("response metadata")(
      test("unary") {
        for {
          result                                      <- unaryEffectWithMd
          ResponseContext(headers, response, trailers) = result
        } yield assert(response)(equalTo(Response("bob"))) &&
          assert(headers)(properHeader) &&
          assert(trailers)(properTrailer)
      },
      test("server streaming") {
        for {
          result <- serverStreamingEffectWithMd
        } yield assert(result.size)(equalTo(4)) &&
          checkHeaderFrame(result(0)) &&
          checkMessageFrame(result(1)) &&
          checkMessageFrame(result(2)) &&
          checkTrailerFrame(result(3))
      },
      test("client streaming") {
        for {
          result                                      <- unaryEffectWithMd
          ResponseContext(headers, response, trailers) = result
        } yield assert(response)(equalTo(Response("bob"))) &&
          assert(headers)(properHeader) &&
          assert(trailers)(properTrailer)
      },
      test("bidi streaming") {
        for {
          result                           <- bidiEffectWithMd
          Chunk(headers, message, trailers) = result
        } yield checkHeaderFrame(headers) &&
          checkMessageFrame(message) &&
          checkTrailerFrame(trailers)
      }
    ).provideLayer(clientMetadataLayer)

  val specs = suite("Metadata")(
    permissionDeniedSuite,
    unauthenticatedSuite,
    authenticatedSuite,
    metadataSuite
  ) @@ TestAspect.timeout(10.seconds)
}
