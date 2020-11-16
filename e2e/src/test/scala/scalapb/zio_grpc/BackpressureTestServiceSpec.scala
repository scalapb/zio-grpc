package scalapb.zio_grpc

import java.util.UUID

import io.grpc.inprocess.{InProcessChannelBuilder, InProcessServerBuilder}
import io.grpc.Status
import scalapb.zio_grpc.backpressure_testservice.Dummy
import scalapb.zio_grpc.backpressure_testservice.ZioBackpressureTestservice.BackpressureTestServiceClient
import zio.clock.Clock
import zio.duration._
import zio._
import zio.stream.Stream
import zio.test.Assertion._
import zio.test.TestAspect.ignore
import zio.test._

/** Test backpressure by sending a huge stream to a slow consumer and counting messages were sent.
  * Use gRPC in-process transport to eliminate TCP buffering.
  * Few messages may still be buffered into internal queues.
  * @note do not use in-process direct executor to avoid bug: [[https://github.com/grpc/grpc-java/issues/3084]]
  */
object BackpressureTestServiceSpec extends DefaultRunnableSpec {

  type UID                     = String
  type HasUID                  = Has[UID]
  type BackpressureTestService = Has[BackpressureTestServiceImpl]

  val SEND_STREAM_SIZE      = 1000L
  val MAX_BUFFERED_MESSAGES = 4

  val waitForBackpressure: UIO[Unit]     = ZIO.sleep(100.millis).provideLayer(Clock.live)
  val dataStream: Stream[Nothing, Dummy] = Stream.repeat(Dummy.defaultInstance).take(SEND_STREAM_SIZE)

  class BackpressureTestServiceImpl(
      serverReceiveOne: Promise[Nothing, Unit],
      serverSentCount: Ref[Int],
      bidiReceiveOne: Promise[Nothing, Unit],
      bidiSentCount: Ref[Int]
  ) extends backpressure_testservice.ZioBackpressureTestservice.BackpressureTestService {

    def clientStreaming(
        request: Stream[Status, Dummy]
    ): ZIO[Any, Status, Dummy] =
      request
        .tap(_ => serverReceiveOne.succeed(()) *> ZIO.never)
        .runDrain
        .as(Dummy.defaultInstance)

    def serverStreaming(
        request: Dummy
    ): Stream[Status, Dummy] =
      Stream.fromEffect(serverSentCount.set(0)).flatMap { _ =>
        dataStream.tap(_ => serverSentCount.update(_ + 1))
      }

    def bidiStreaming(
        request: Stream[Status, Dummy]
    ): Stream[Status, Dummy] =
      for {
        _           <- Stream.fromEffect(bidiSentCount.set(0))
        drainRequest = request
                         .tap(_ => bidiReceiveOne.succeed(()) *> ZIO.never)
                         .runDrain
                         .fork
        _           <- Stream.fromEffect(drainRequest *> bidiReceiveOne.await)
        res         <- dataStream.tap(_ => bidiSentCount.update(_ + 1))
      } yield res

    def awaitServerReceiveOne: UIO[Unit] = serverReceiveOne.await

    def getServerSentCount: UIO[Int] = serverSentCount.get

    def awaitBidiReceiveOne: UIO[Unit] = bidiReceiveOne.await

    def getBidiSentCount: UIO[Int] = bidiSentCount.get
  }

  def clientStreamingSuite =
    testM("client streaming supports backpressure") {
      assertM(
        for {
          clientSentCount <- Ref.make(0)
          clientStream     = dataStream.tap(_ => clientSentCount.update(_ + 1))
          _               <- BackpressureTestServiceClient.clientStreaming(clientStream).fork
          _               <- ZIO.accessM[BackpressureTestService](_.get.awaitServerReceiveOne)
          _               <- waitForBackpressure
          sentCount       <- clientSentCount.get
        } yield sentCount
      )(isLessThanEqualTo(MAX_BUFFERED_MESSAGES))
    }

  def serverStreamingSuite =
    testM("server streaming supports backpressure") {
      assertM(
        for {
          clientReceiveOne <- Promise.make[Nothing, Unit]
          _                <- BackpressureTestServiceClient
                                .serverStreaming(Dummy.defaultInstance)
                                .tap(_ => clientReceiveOne.succeed(()) *> ZIO.never)
                                .runDrain
                                .fork
          _                <- clientReceiveOne.await
          _                <- waitForBackpressure
          sentCount        <- ZIO.accessM[BackpressureTestService](_.get.getServerSentCount)
        } yield sentCount
      )(isLessThanEqualTo(MAX_BUFFERED_MESSAGES))
    }

  def bidiStreamingSuite =
    testM("bidi streaming supports backpressure") {
      assertM(
        for {
          clientSentCount <- Ref.make(0)
          receiveOne      <- Promise.make[Nothing, Unit]
          clientStream     = dataStream.tap(_ => clientSentCount.update(_ + 1))
          _               <- BackpressureTestServiceClient
                               .bidiStreaming(clientStream)
                               .tap(_ => receiveOne.succeed(()) *> ZIO.never)
                               .runDrain
                               .fork
          _               <- receiveOne.await
          _               <- ZIO.accessM[BackpressureTestService](_.get.awaitBidiReceiveOne)
          _               <- waitForBackpressure
          clientSentCount <- clientSentCount.get
          serverSentCount <- ZIO.accessM[BackpressureTestService](_.get.getBidiSentCount)
        } yield clientSentCount + serverSentCount
      )(isLessThanEqualTo(MAX_BUFFERED_MESSAGES * 2))
    }

  val uidLayer: ULayer[HasUID] = ZLayer.fromEffect(ZIO.succeed(UUID.randomUUID().toString))

  val serviceLayer: ULayer[BackpressureTestService] =
    ZLayer.fromEffect {
      for {
        promise1 <- Promise.make[Nothing, Unit]
        promise2 <- Promise.make[Nothing, Unit]
        ref1     <- Ref.make(0)
        ref2     <- Ref.make(0)
      } yield new BackpressureTestServiceImpl(promise1, ref1, promise2, ref2)
    }

  val serverLayer: ZLayer[HasUID with BackpressureTestService, Throwable, Server] =
    ZLayer.fromManaged {
      (for {
        uid     <- ZManaged.service[UID]
        service <- ZManaged.service[BackpressureTestServiceImpl]
        builder  = InProcessServerBuilder.forName(uid)
        services = ServiceList.add(service)
      } yield ManagedServer.fromServiceList(builder, services)).flatten
    }

  val clientLayer: ZLayer[HasUID, Nothing, BackpressureTestServiceClient] =
    ZLayer.fromManaged {
      ZManaged.service[UID].flatMap { uid =>
        BackpressureTestServiceClient
          .managed(ZManagedChannel(InProcessChannelBuilder.forName(uid)))
          .orDie
      }
    }

  val layers = (uidLayer ++ serviceLayer) >+> serverLayer ++ clientLayer ++ Annotations.live

  //TODO remove ignore aspect after switching to the backpressure supports calls
  def spec =
    suite("BackpressureTestServiceSpec")(
      clientStreamingSuite,
      serverStreamingSuite,
      bidiStreamingSuite
    ).provideCustomLayer(layers.orDie) @@ ignore
}
