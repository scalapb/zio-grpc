package scalapb.zio_grpc

import io.grpc.ManagedChannelBuilder
import io.grpc.ServerBuilder
import scalapb.zio_grpc.helloworld.testservice._
import zio._
import java.time

object ServerStreamingBenchmarkApp extends ZIOAppDefault {

  val size = 100000L

  val server =
    ServerLayer.fromEnvironment[ZioTestservice.Greeter](ServerBuilder.forPort(50051))

  val client =
    ZLayer.scoped[Server] {
      for {
        ss     <- ZIO.service[Server]
        port   <- ss.port.orDie
        ch      = ManagedChannelBuilder.forAddress("localhost", 50051).usePlaintext()
        client <- ZioTestservice.GreeterClient.scoped(ZManagedChannel(ch)).orDie
      } yield client
    }

  val service =
    ZLayer.succeed[ZioTestservice.Greeter] {
      new GreeterImpl(size)
    }

  def run = ZIO
    .foreach(Array(8192, 65536)) { queueSize =>
      val props = java.lang.System.getProperties();
      props.setProperty("zio-grpc.backpressure-queue-size", queueSize.toString());

      for {
        _      <- Console.printLine(s"Starting with queue size $queueSize")
        cpt    <- Ref.make(0)
        start  <- Clock.instant.flatMap(Ref.make(_))
        result <- ZioTestservice.GreeterClient
                    .sayHelloStreaming(HelloRequest(request = Some(Hello(name = "Testing streaming"))))
                    .tap(_ => cpt.update(_ + 1))
                    .tap { _ =>
                      for {
                        now     <- Clock.instant
                        started <- start.get
                        _       <- ZIO.when(time.Duration.between(started, now).getSeconds() >= 10)(
                                     start.set(now) *> cpt.get.flatMap(cpt => Console.printLine(s"Received $cpt messages"))
                                   )
                      } yield ()
                    }
                    .runDrain
                    .timed
        _      <- Console.printLine(s"queue size: $queueSize (${result._1.toMillis()}ms)")
      } yield ()
    }
    .provide(service >+> server >+> client)

}
