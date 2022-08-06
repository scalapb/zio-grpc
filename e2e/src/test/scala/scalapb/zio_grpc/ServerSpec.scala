package scalapb.zio_grpc

import zio._
import zio.test._
import zio.test.Assertion._
import java.util.concurrent.TimeUnit
import java.time.Duration

object ServerSpec extends DefaultRunnableSpec {
  val spec =
    suite("Server")(
      testM("Awaits termination") {
        for {
          waitedRef <- Ref.make(false)
          runtime   <- ZIO.runtime[Any]
          server     = new io.grpc.Server {
                         def awaitTermination(): Unit = runtime.unsafeRun(waitedRef.set(true))

                         def start(): io.grpc.Server                                  = ???
                         def shutdown(): io.grpc.Server                               = ???
                         def shutdownNow(): io.grpc.Server                            = ???
                         def isShutdown(): Boolean                                    = ???
                         def isTerminated(): Boolean                                  = ???
                         def awaitTermination(timeout: Long, unit: TimeUnit): Boolean = ???
                       }
          pbServer   = new Server.ServiceImpl(server)
          _         <- pbServer.awaitTermination
          waited    <- waitedRef.get
        } yield assert(waited)(isTrue)
      },
      testM("Awaits termination with timeout") {
        for {
          waitedRef <- Ref.make[BoolAlgebra[AssertionResult]](assert(false)(isTrue))
          runtime   <- ZIO.runtime[Any]
          server     = new io.grpc.Server {
                         def awaitTermination(timeout: Long, unit: TimeUnit): Boolean = {
                           val assertion1 = assert(timeout)(equalTo(2500L))
                           val assertion2 = assert(unit)(equalTo(TimeUnit.MILLISECONDS))
                           runtime.unsafeRun(
                             waitedRef.set(assertion1 && assertion2)
                           )
                           true
                         }

                         def start(): io.grpc.Server       = ???
                         def shutdown(): io.grpc.Server    = ???
                         def shutdownNow(): io.grpc.Server = ???
                         def isShutdown(): Boolean         = ???
                         def isTerminated(): Boolean       = ???
                         def awaitTermination(): Unit      = ???
                       }
          pbServer   = new Server.ServiceImpl(server)
          _         <- pbServer.awaitTermination(Duration.ofSeconds(2, 500000000L))
          assertion <- waitedRef.get
        } yield assertion
      }
    )
}
