package scalapb.zio_grpc

import io.grpc.{ClientCall, Metadata, Status, StatusException}
import scalapb.zio_grpc.client._
import zio._
import zio.test._

object ClientCompatibilitySpec extends ZIOSpecDefault {
  // Deliberately implements only the API that existed before outbound backpressure.
  private class LegacyCall extends ZClientCall[String, String] {
    def start(listener: ClientCall.Listener[String], headers: SafeMetadata) = ZIO.unit
    def request(n: Int)                                                     = ZIO.unit
    def cancel(message: String)                                             = ZIO.unit
    def halfClose()                                                         = ZIO.unit
    def sendMessage(message: String)                                        = ZIO.unit
  }

  def spec = suite("client API compatibility")(
    test("an existing custom call remains ready without implementing isReady") {
      new LegacyCall().isReady.map(ready => assertTrue(ready))
    },
    test("forwarding calls preserve readiness") {
      val call = new LegacyCall {
        override def isReady = ZIO.succeed(false)
      }
      new ZClientCall.ForwardingZClientCall(call).isReady.map(ready => assertTrue(!ready))
    },
    test("the original unary listener constructor remains usable") {
      for {
        runtime <- ZIO.runtime[Any]
        state   <- Ref.make[UnaryCallState[String]](UnaryCallState.Initial)
        promise <- Promise.make[StatusException, ResponseContext[String]]
        listener = new UnaryClientCallListener(runtime, state, promise)
        _       <- ZIO.succeed {
                     listener.onReady()
                     listener.onHeaders(new Metadata)
                     listener.onMessage("response")
                     listener.onClose(Status.OK, new Metadata)
                   }
        result  <- listener.getValue
      } yield assertTrue(result.response == "response")
    },
    test("the original streaming listener constructor remains usable") {
      for {
        runtime <- ZIO.runtime[Any]
        queue   <- Queue.unbounded[ResponseFrame[String]]
        listener = new StreamingClientCallListener(None, runtime, new LegacyCall, queue)
        _       <- ZIO.succeed {
                     listener.onReady()
                     listener.onHeaders(new Metadata)
                     listener.onMessage("response")
                     listener.onClose(Status.OK, new Metadata)
                   }
        result  <- listener.stream.runCollect
      } yield assertTrue(result.collect { case ResponseFrame.Message(m) => m } == Chunk("response"))
    }
  )
}
