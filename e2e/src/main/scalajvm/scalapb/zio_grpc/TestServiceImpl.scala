package scalapb.zio_grpc

import scalapb.zio_grpc.testservice.Request
import zio._
import scalapb.zio_grpc.testservice.Response
import io.grpc.{Status, StatusException}
import scalapb.zio_grpc.testservice.Request.Scenario
import zio.stream.{Stream, ZStream}

package object server {

  import zio.Schedule

  type TestServiceImpl = TestServiceImpl.Service

  object TestServiceImpl {

    class Service(
        requestReceived: Promise[Nothing, Unit],
        delayReceived: Promise[Nothing, Unit],
        exit: Promise[Nothing, Exit[StatusException, Response]],
        responseCounter: Ref[Int],
        rpcCounter: Ref[Int]
    )(clock: Clock, console: Console)
        extends testservice.ZioTestservice.TestService {

      // A response of size 100KB to saturate the byte buffers and observe backpressure.
      private val largeResponse = Response("*" * 100000)

      def unary(request: Request): ZIO[Any, StatusException, Response] = {
        def run(request: Request) = request.scenario match {
          case Scenario.OK          =>
            ZIO.succeed(Response(out = "Res" + request.in.toString))
          case Scenario.ERROR_NOW   =>
            ZIO.fail(Status.INTERNAL.withDescription("FOO!").asException())
          case Scenario.DELAY       =>
            ZIO.never
          case Scenario.DIE         =>
            ZIO.die(new RuntimeException("FOO"))
          case Scenario.UNAVAILABLE =>
            rpcCounter.get.flatMap[Any, StatusException, Nothing] { n =>
              ZIO.fail(Status.UNAVAILABLE.withDescription(n.toString).asException())
            }
          case _                    =>
            ZIO.fail(Status.UNKNOWN.asException())
        }

        (requestReceived.succeed(()) *> rpcCounter.incrementAndGet *> run(request) <* responseCounter.incrementAndGet)
          .onExit(exit.succeed(_))
      }

      def unaryTypeMapped(request: Request): ZIO[Any, StatusException, WrappedString] =
        unary(request).map(r => WrappedString(r.out))

      def serverStreaming(request: Request): ZStream[Any, StatusException, Response] = {
        def run(request: Request) = request.scenario match {
          case Scenario.OK           =>
            ZStream(Response(out = "X1"), Response(out = "X2"))
          case Scenario.ERROR_NOW    =>
            ZStream.fail(Status.INTERNAL.withDescription("FOO!").asException())
          case Scenario.ERROR_AFTER  =>
            ZStream(Response(out = "X1"), Response(out = "X2")) ++
              ZStream.fail(Status.INTERNAL.withDescription("FOO!").asException())
          case Scenario.DELAY        =>
            ZStream(Response(out = "X1"), Response(out = "X2")) ++ ZStream.never
          case Scenario.LARGE_STREAM =>
            ZStream.fromIterator(Iterator.fill(100)(largeResponse), 1).orDie
          case Scenario.DIE          =>
            ZStream.die(new RuntimeException("FOO"))
          case _                     =>
            ZStream.fail(Status.UNKNOWN.asException())
        }

        ZStream.acquireReleaseExitWith(requestReceived.succeed(()) *> rpcCounter.incrementAndGet) { (_, ex) =>
          ex.foldExit(
            { failed =>
              val status = if (failed.isInterrupted) Status.CANCELLED else Status.UNKNOWN
              exit.succeed(Exit.fail(status.asException))
            },
            _ => exit.succeed(Exit.succeed(Response()))
          )
        } *> run(request).tapChunks(chunk => responseCounter.update(_ + chunk.size))
      }

      def serverStreamingTypeMapped(request: Request): ZStream[Any, StatusException, WrappedString] =
        serverStreaming(request).map(r => WrappedString(r.out))

      def clientStreaming(request: Stream[StatusException, Request]): ZIO[Any, StatusException, Response] = {
        def run(state: Int, request: Request) = request.scenario match {
          case Scenario.OK        =>
            ZIO.succeed(state + request.in)
          case Scenario.DELAY     =>
            delayReceived.succeed(()) *> ZIO.never
          case Scenario.DIE       =>
            ZIO.die(new RuntimeException("foo"))
          case Scenario.ERROR_NOW =>
            ZIO.fail(Status.INTERNAL.withDescription("InternalError").asException())
          case _: Scenario        =>
            ZIO.fail(Status.UNKNOWN.asException())
        }

        requestReceived.succeed(()) *> rpcCounter.incrementAndGet *> request
          .runFoldZIO(0)(run)
          .map(r => Response(r.toString))
          .zipLeft(responseCounter.incrementAndGet)
          .onExit(exit.succeed(_))
      }

      def bidiStreaming(
          request: Stream[StatusException, Request]
      ): Stream[StatusException, Response] = {
        def run(request: Request) = request.scenario match {
          case Scenario.OK        =>
            ZStream(Response(request.in.toString)).repeat(Schedule.recurs(request.in - 1))
          case Scenario.DELAY     =>
            ZStream.never
          case Scenario.DIE       =>
            ZStream.die(new RuntimeException("FOO"))
          case Scenario.ERROR_NOW =>
            ZStream.fail(Status.INTERNAL.withDescription("Intentional error").asException())
          case _                  =>
            ZStream.fail(
              Status.INVALID_ARGUMENT.withDescription(s"Got request: ${request.toProtoString}").asException()
            )
        }

        ZStream.execute(requestReceived.succeed(()) *> rpcCounter.incrementAndGet) ++ request
          .flatMap(run)
          .concat(ZStream(Response("DONE")))
          .tapChunks(chunk => responseCounter.update(_ + chunk.size))
          .ensuring(exit.succeed(Exit.succeed(Response())))
          .provideEnvironment(ZEnvironment(clock, console))
      }

      def awaitReceived      = requestReceived.await
      def awaitDelayReceived = delayReceived.await
      def awaitExit          = exit.await
      def responsesSent      = responseCounter.get
    }

    def make(
        clock: Clock,
        console: Console
    ): IO[Nothing, TestServiceImpl.Service] = for {
      p1 <- Promise.make[Nothing, Unit]
      p2 <- Promise.make[Nothing, Unit]
      p3 <- Promise.make[Nothing, Exit[StatusException, Response]]
      c1 <- Ref.make(0)
      c2 <- Ref.make(0)
    } yield new Service(p1, p2, p3, c1, c2)(clock, console)

    def makeFromEnv: ZIO[Any, Nothing, Service] = for {
      clock   <- ZIO.clock
      console <- ZIO.console
      service <- make(clock, console)
    } yield service

    val live: ZLayer[Any, Nothing, TestServiceImpl] =
      ZLayer.scoped(makeFromEnv)

    val any: ZLayer[TestServiceImpl, Nothing, TestServiceImpl] =
      ZLayer.environment

    def awaitReceived: ZIO[TestServiceImpl, Nothing, Unit] =
      ZIO.environmentWithZIO(_.get.awaitReceived)

    def awaitDelayReceived: ZIO[TestServiceImpl, Nothing, Unit] =
      ZIO.environmentWithZIO(_.get.awaitDelayReceived)

    def awaitExit: ZIO[TestServiceImpl, Nothing, Exit[StatusException, Response]] =
      ZIO.environmentWithZIO(_.get.awaitExit)

    def responsesSent: ZIO[TestServiceImpl, Nothing, Int] =
      ZIO.environmentWithZIO(_.get.responsesSent)
  }
}
