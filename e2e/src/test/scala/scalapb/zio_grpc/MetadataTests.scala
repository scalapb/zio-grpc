package scalapb.zio_grpc

import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.duration._
import zio._
import zio.stream.Stream
import testservice.ZioTestservice.TestServiceClient
import testservice._
import io.grpc.Status
import TestUtils._

trait MetadataTests {
  def clientLayer(
      userName: Option[String]
  ): ZLayer[Server, Nothing, TestServiceClient]

  val authClient   = clientLayer(Some("bob"))
  val unauthClient = clientLayer(Some("alice"))
  val unsetClient  = clientLayer(None)

  val permissionDenied = fails(hasStatusCode(Status.PERMISSION_DENIED))
  val unauthenticated  = fails(hasStatusCode(Status.UNAUTHENTICATED))

  val unaryEffect           = TestServiceClient.unary(Request())
  val serverStreamingEffect =
    TestServiceClient.serverStreaming(Request()).runCollect
  val clientStreamingEffect = TestServiceClient.clientStreaming(Stream.empty)
  val bidiEffect            = TestServiceClient.bidiStreaming(Stream.empty).runCollect

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

  val specs = suite("Metadata")(
    permissionDeniedSuite,
    unauthenticatedSuite,
    authenticatedSuite
  ) @@ timeout(10.seconds)
}
