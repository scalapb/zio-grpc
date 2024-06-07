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
  def mockConfigProvider(value: String): ZLayer[Any, Nothing, Unit] =
    Runtime.setConfigProvider(ConfigProvider.fromMap(Map(ZServerCallHandler.queueSizeProp -> value)))

  val spec =
    suite("Backpressure")(
      test("Die is propagated") {
        assertZIO(for {
          sem  <- TSemaphore.make(1).commit
          exit <-
            ZServerCallHandler
              .serverStreamingWithBackpressure(
                new ZServerCall(null, sem),
                ZStream.die(new RuntimeException("Boom"))
              )
              .exit
        } yield exit)(dies(hasMessage(equalTo("Boom"))))
      },
      test("Gets default queue size") {
        assertZIO(ZServerCallHandler.backpressureQueueSize)(equalTo(16))
      },
      test("Gets queue size from config") {
        assertZIO(for {
          size <- ZServerCallHandler.backpressureQueueSize
        } yield size)(equalTo(32))
      }.provideLayer(mockConfigProvider("32")),
      test("Fails when queue size is malformatted") {
        assertZIO(for {
          size <- ZServerCallHandler.backpressureQueueSize.exit
        } yield size)(fails(anything))
      }.provideLayer(mockConfigProvider(" 32 ")),
      test("Interruption is propagated") {
        assertZIO(for {
          sem  <- TSemaphore.make(1).commit
          exit <- ZServerCallHandler
                    .serverStreamingWithBackpressure(new ZServerCall(null, sem), ZStream.fromZIO(ZIO.interrupt))
                    .exit
        } yield exit)(isInterrupted)
      },
      test("Normal execution") {
        val input = List.iterate(0, 100)(_ + 1)

        for {
          sem     <- TSemaphore.make(1).commit
          runtime <- ZIO.runtime[Any]
          ref     <- Ref.make(List.empty[Int])
          call     = new ServerCall[Any, Int]() {
                       override def sendMessage(message: Int): Unit                                 = {
                         val _ = Unsafe.unsafe { implicit u =>
                           runtime.unsafe.run(ref.update(message :: _))
                         }
                       }
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
                       .serverStreamingWithBackpressure(new ZServerCall(call, sem), ZStream.fromIterable(input))
                       .exit
          result  <- ref.get
        } yield assert(exit)(succeeds(equalTo(()))) && assert(result)(hasSameElements(input))
      }
    ) @@ TestAspect.sequential

}
