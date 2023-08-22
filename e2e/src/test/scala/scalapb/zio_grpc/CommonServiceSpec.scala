package scalapb.zio_grpc

import zio.test._
import zio.test.Assertion._
import TestUtils._
import scalapb.zio_grpc.testservice.ZioTestservice.TestServiceClient
import scalapb.zio_grpc.testservice._
import io.grpc.Status

trait CommonTestServiceSpec {
  this: ZIOSpecDefault =>

  def unarySuite =
    suite("unary request (common)")(
      test("returns successful response") {
        assertZIO(TestServiceClient.unary(Request(Request.Scenario.OK, in = 12)))(
          equalTo(Response("Res12"))
        )
      },
      test("returns successful response when the program is used repeatedly") {
        // Must not capture an instance of ZClientCall, so call.start() should not be invoked twice
        assertZIO(TestServiceClient.unary(Request(Request.Scenario.OK, in = 12)).repeatN(1))(
          equalTo(Response("Res12"))
        )
      },
      test("returns correct error response") {
        assertZIO(
          TestServiceClient
            .unary(Request(Request.Scenario.ERROR_NOW, in = 12))
            .exit
        )(
          fails(hasStatusCode(Status.INTERNAL) && hasMetadataKey("foo-bin"))
        )
      },
      test("returns response on failures") {
        assertZIO(
          TestServiceClient.unary(Request(Request.Scenario.DIE, in = 12)).exit
        )(
          fails(hasStatusCode(Status.INTERNAL))
        )
      },
      test("successful type mapped response") {
        assertZIO(TestServiceClient.unaryTypeMapped(Request(Request.Scenario.OK, in = 15)))(
          equalTo(WrappedString("Res15"))
        )
      }
    )

  def serverStreamingSuite =
    suite("server streaming request (common)")(
      test("returns successful response") {
        assertZIO(
          collectWithError(
            TestServiceClient.serverStreaming(
              Request(Request.Scenario.OK, in = 12)
            )
          )
        )(equalTo((List(Response("X1"), Response("X2")), None)))
      },
      test("returns successful typemapped response") {
        assertZIO(
          collectWithError(
            TestServiceClient.serverStreamingTypeMapped(
              Request(Request.Scenario.OK, in = 12)
            )
          )
        )(equalTo((List(WrappedString("X1"), WrappedString("X2")), None)))
      },
      test("returns correct error response") {
        assertZIO(
          collectWithError(
            TestServiceClient.serverStreaming(
              Request(Request.Scenario.ERROR_NOW, in = 12)
            )
          )
        )(
          tuple(isEmpty, isSome(hasStatusCode(Status.INTERNAL)))
        )
      },
      test("returns correct error after two response") {
        assertZIO(
          collectWithError(
            TestServiceClient.serverStreaming(
              Request(Request.Scenario.ERROR_AFTER, in = 12)
            )
          )
        )(
          tuple(
            hasSize(equalTo(2)),
            isSome(hasStatusCode(Status.INTERNAL))
          )
        )
      },
      test("returns failure when failure") {
        assertZIO(
          collectWithError(
            TestServiceClient.serverStreaming(
              Request(Request.Scenario.DIE, in = 12)
            )
          )
        )(
          tuple(isEmpty, isSome(hasStatusCode(Status.INTERNAL)))
        )
      }
    )
}
