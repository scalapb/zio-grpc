package scalapb.zio_grpc

import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.duration._
import zio._
import zio.stream.Stream
import testservice.ZioTestservice.{TestServiceClient, TestServiceClientWithMetadata}
import testservice._
import io.grpc.Status
import TestUtils._
import io.grpc.Metadata

trait MetadataTests {
  def clientLayer(
      userName: Option[String]
  ): ZLayer[Server, Nothing, TestServiceClient]

  def clientMetadataLayer: ZLayer[Server, Nothing, TestServiceClientWithMetadata]

  val RequestIdKey =
    Metadata.Key.of("request-id", io.grpc.Metadata.ASCII_STRING_MARSHALLER)

  val authClient   = clientLayer(Some("bob"))
  val unauthClient = clientLayer(Some("alice"))
  val errorClient  = clientLayer(Some("Eve"))
  val unsetClient  = clientLayer(None)

  val permissionDenied  = fails(hasStatusCode(Status.PERMISSION_DENIED))
  val unauthenticated   = fails(hasStatusCode(Status.UNAUTHENTICATED))
  val errorWithTrailers = fails(hasStatusCode(Status.FAILED_PRECONDITION) && hasTrailerValue(RequestIdKey, "1"))

  val unaryEffect           = TestServiceClient.unary(Request())
  val serverStreamingEffect =
    TestServiceClient.serverStreaming(Request()).runCollect
  val clientStreamingEffect = TestServiceClient.clientStreaming(Stream.empty)
  val bidiEffect            = TestServiceClient.bidiStreaming(Stream.empty).runCollect

  val unaryEffectWithMd           =
    ZioTestservice.TestServiceClientWithMetadata.unary(Request())
  val serverStreamingEffectWithMd =
    ZioTestservice.TestServiceClientWithMetadata.serverStreaming(Request()).runCollect
  val clientStreamingEffectWithMd =
    ZioTestservice.TestServiceClientWithMetadata.clientStreaming(Stream.empty)
  val bidiEffectWithMd            =
    ZioTestservice.TestServiceClientWithMetadata.bidiStreaming(Stream.empty).runCollect

  val properTrailer = Assertion.assertion[Metadata]("trailers")() { md =>
    md.containsKey(RequestIdKey) && md.get(RequestIdKey) == "1"
  }

  val properHeader = Assertion.assertion[Metadata]("headers")() { md =>
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
    suite("authorized request")(
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

  def errorWithTrailersSuite =
    suite("errro response with trailers")(
      testM("unary") {
        assertM(unaryEffect.run)(errorWithTrailers)
      },
      testM("server streaming") {
        assertM(serverStreamingEffect.run)(errorWithTrailers)
      },
      testM("client streaming") {
        assertM(clientStreamingEffect.run)(errorWithTrailers)
      },
      testM("bidi streaming") {
        assertM(bidiEffect.run)(errorWithTrailers)
      }
    ).provideLayer(errorClient)

  def metadataSuite =
    suite("response metadata")(
      testM("unary") {
        for {
          result                                      <- unaryEffectWithMd
          ResponseContext(headers, response, trailers) = result
        } yield assert(response)(equalTo(Response("bob"))) &&
          assert(headers)(properHeader) &&
          assert(trailers)(properTrailer)
      },
      testM("server streaming") {
        for {
          result <- serverStreamingEffectWithMd
        } yield assert(result.size)(equalTo(4)) &&
          checkHeaderFrame(result(0)) &&
          checkMessageFrame(result(1)) &&
          checkMessageFrame(result(2)) &&
          checkTrailerFrame(result(3))
      },
      testM("client streaming") {
        for {
          result                                      <- unaryEffectWithMd
          ResponseContext(headers, response, trailers) = result
        } yield assert(response)(equalTo(Response("bob"))) &&
          assert(headers)(properHeader) &&
          assert(trailers)(properTrailer)
      },
      testM("bidi streaming") {
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
    errorWithTrailersSuite,
    metadataSuite
  ) @@ timeout(120.seconds)
}
