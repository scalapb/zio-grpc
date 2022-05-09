package scalapb.zio_grpc

import zio.ZLayer
import zio.test._
import zio.test.Assertion._
import zio.stream.ZStream
import io.grpc.Status
import TestUtils._
import scalapb.zio_grpc.testservice._
import scalapb.zio_grpc.testservice.ZioTestservice._

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
  val clientStreamingEffect = TestServiceClient.clientStreaming(ZStream.empty)
  val bidiEffect            = TestServiceClient.bidiStreaming(ZStream.empty).runCollect

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
    suite("authorized request fail for")(
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

  val specs = Seq(
    permissionDeniedSuite,
    unauthenticatedSuite,
    authenticatedSuite
  )
}
