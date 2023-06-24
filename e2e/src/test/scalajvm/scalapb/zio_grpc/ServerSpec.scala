package scalapb.zio_grpc

import zio._
import zio.test._
import zio.test.Assertion._
import java.util.concurrent.TimeUnit
import java.time.Duration

object ServerSpec extends ZIOSpecDefault {
  val spec =
    suite("Server")(
      test("awaitsTermination delegates to underlying implementation (without timeout)") {
        for {
          waitedRef <- Ref.make(false)
          server     = new io.grpc.Server {
                         def awaitTermination(): Unit = {
                           val _ = Unsafe.unsafe { implicit unsafe =>
                             Runtime.default.unsafe.run(waitedRef.set(true)).getOrThrowFiberFailure()
                           }
                         }

                         def start(): io.grpc.Server                                  = ???
                         def shutdown(): io.grpc.Server                               = ???
                         def shutdownNow(): io.grpc.Server                            = ???
                         def isShutdown(): Boolean                                    = ???
                         def isTerminated(): Boolean                                  = ???
                         def awaitTermination(timeout: Long, unit: TimeUnit): Boolean = ???
                       }
          pbServer   = new ServerImpl(server)
          _         <- pbServer.awaitTermination
          waited    <- waitedRef.get
        } yield assert(waited)(isTrue)
      },
      test("awaitsTermination delegates to underlying implementation (with timeout)") {
        for {
          waitedRef <- Ref.make[TestResult](assert(false)(isTrue))
          runtime   <- ZIO.runtime[Any]
          server     = new io.grpc.Server {
                         def awaitTermination(timeout: Long, unit: TimeUnit): Boolean = {
                           val assertion1 = assert(timeout)(equalTo(2500L))
                           val assertion2 = assert(unit)(equalTo(TimeUnit.MILLISECONDS))
                           Unsafe.unsafe { implicit unsafe =>
                             Runtime.default.unsafe.run(waitedRef.set(assertion1 && assertion2)).getOrThrowFiberFailure()
                           }
                           true
                         }

                         def start(): io.grpc.Server       = ???
                         def shutdown(): io.grpc.Server    = ???
                         def shutdownNow(): io.grpc.Server = ???
                         def isShutdown(): Boolean         = ???
                         def isTerminated(): Boolean       = ???
                         def awaitTermination(): Unit      = ???
                       }
          pbServer   = new ServerImpl(server)
          _         <- pbServer.awaitTermination(Duration.ofSeconds(2, 500000000L))
          assertion <- waitedRef.get
        } yield assertion
      }
    )
}
