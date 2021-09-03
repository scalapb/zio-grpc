package scalapb.zio_grpc

import zio.ZLayer
import zio.test._
import zio.test.Assertion._
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
      test("unary") {
        assertM(unaryEffect.run)(permissionDenied)
      },
      test("server streaming") {
        assertM(serverStreamingEffect.run)(permissionDenied)
      },
      test("client streaming") {
        assertM(clientStreamingEffect.run)(permissionDenied)
      },
      test("bidi streaming") {
        assertM(bidiEffect.run)(permissionDenied)
      }
    ).provideLayer(unauthClient)

  def unauthenticatedSuite =
    suite("authorized request fail for")(
      test("unary") {
        assertM(unaryEffect.run)(unauthenticated)
      },
      test("server streaming") {
        assertM(serverStreamingEffect.run)(unauthenticated)
      },
      test("client streaming") {
        assertM(clientStreamingEffect.run)(unauthenticated)
      },
      test("bidi streaming") {
        assertM(bidiEffect.run)(unauthenticated)
      }
    ).provideLayer(unsetClient)

  def authenticatedSuite =
    suite("authorized request fail for")(
      test("unary") {
        assertM(unaryEffect)(equalTo(Response("bob")))
      },
      test("server streaming") {
        assertM(serverStreamingEffect)(
          equalTo(Seq(Response("bob"), Response("bob")))
        )
      },
      test("client streaming") {
        assertM(clientStreamingEffect)(equalTo(Response("bob")))
      },
      test("bidi streaming") {
        assertM(bidiEffect)(equalTo(Seq(Response("bob"))))
      }
    ).provideLayer(authClient)

  val specs = Seq(
    permissionDeniedSuite,
    unauthenticatedSuite,
    authenticatedSuite
  )
}
