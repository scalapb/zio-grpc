package scalapb.zio_grpc

import zio._
import zio.stream._
import zio.test._
import zio.test.Assertion._
import scalapb.zio_grpc.server.ZServerCallHandler
import scalapb.zio_grpc.server.ZServerCall
import zio.stm.TSemaphore
import io.grpc.Attributes
import io.grpc.ServerCall

object BackpressureSpec extends ZIOSpecDefault {
  val spec =
    suite("Backpressure")(
      test("Die is propagated") {
        assertZIO(for {
          sem  <- TSemaphore.makeCommit(1)
          md   <- SafeMetadata.make
          ctx   = RequestContext(md, md, None, null, Attributes.EMPTY)
          exit <-
            ZServerCallHandler
              .serverStreamingWithBackpressure(
                new ZServerCall(null),
                sem,
                ctx,
                ZStream.die(new RuntimeException("Boom"))
              )
              .exit
        } yield exit)(dies(hasMessage(equalTo("Boom"))))
      },
      test("Interruption is propagated") {
        assertZIO(for {
          sem  <- TSemaphore.makeCommit(1)
          md   <- SafeMetadata.make
          ctx   = RequestContext(md, md, None, null, Attributes.EMPTY)
          exit <- ZServerCallHandler
                    .serverStreamingWithBackpressure(new ZServerCall(null), sem, ctx, ZStream.fromZIO(ZIO.interrupt))
                    .exit
        } yield exit)(isInterrupted)
      },
      test("Normal execution") {
        val input = List.iterate(0, 100)(_ + 1)

        for {
          sem     <- TSemaphore.makeCommit(1)
          md      <- SafeMetadata.make
          runtime <- ZIO.runtime[Any]
          ref     <- Ref.make(List.empty[Int])
          ctx      = RequestContext(md, md, None, null, Attributes.EMPTY)
          call     = new ServerCall[Any, Int]() {
                       override def sendMessage(message: Int): Unit =
                         Unsafe.unsafe(implicit u => runtime.unsafe.run(ref.update(message :: _)).getOrThrowFiberFailure())

                       var listener: ServerCall.Listener[Any]                                       = null
                       override def request(numMessages: Int): Unit                                 = ()
                       override def sendHeaders(headers: io.grpc.Metadata): Unit                    = ()
                       override def close(status: io.grpc.Status, trailers: io.grpc.Metadata): Unit = ()
                       override def isReady: Boolean                                                = true
                       override def setCompression(s: String): Unit                                 = ()
                       override def setMessageCompression(enabled: Boolean): Unit                   = ()
                       override def getAttributes: Attributes                                       = Attributes.EMPTY
                       override def getMethodDescriptor: io.grpc.MethodDescriptor[Any, Int]         = null
                       override def isCancelled(): Boolean                                          = false
                     }
          exit    <- ZServerCallHandler
                       .serverStreamingWithBackpressure(new ZServerCall(call), sem, ctx, ZStream.fromIterable(input))
                       .exit
          result  <- ref.get
        } yield assert(exit)(succeeds(equalTo(()))) && assert(result)(hasSameElements(input))
      }
    )

}
