package scalapb.zio_grpc

import io.grpc._
import scalapb.zio_grpc.client.{ClientCalls, ZClientCall}
import scalapb.zio_grpc.testservice.{Request, Response, TestServiceGrpc}
import zio._
import zio.stream.ZStream
import zio.test._

object RequestStreamBackpressureSpec extends ZIOSpecDefault {
  private val trailerKey = Metadata.Key.of("test-detail", Metadata.ASCII_STRING_MARSHALLER)

  private case class Transport(
      listener: Promise[Nothing, ClientCall.Listener[Response]],
      readiness: Ref[UIO[Boolean]],
      checked: Queue[Unit],
      sent: Queue[Request],
      halfClosed: Promise[Nothing, Unit],
      cancellations: Ref[Int],
      initialize: Ref[IO[StatusException, Unit]],
      finish: Ref[IO[StatusException, Unit]]
  ) extends ZClientCall[Request, Response] {
    def start(l: ClientCall.Listener[Response], headers: SafeMetadata) = listener.succeed(l) *> initialize.get.flatten
    def request(n: Int)                                                = ZIO.unit
    def cancel(message: String)                                        = cancellations.update(_ + 1)
    def halfClose()                                                    = halfClosed.succeed(()) *> finish.get.flatten
    override def isReady                                               = readiness.get.flatten.flatMap(r => checked.offer(()).as(r))
    def sendMessage(message: Request)                                  = sent.offer(message).unit

    val channel = new ZChannel(null, None, Nil) {
      override def newCall[Req, Res](method: MethodDescriptor[Req, Res], options: CallOptions) =
        ZIO.succeed(Transport.this.asInstanceOf[ZClientCall[Req, Res]])
    }

    def onReady                           = listener.await.flatMap(l => ZIO.succeed(l.onReady()))
    def close(status: Status = Status.OK) = listener.await.flatMap { l =>
      ZIO.succeed {
        val trailers = new Metadata
        trailers.put(trailerKey, "original trailers")
        l.onHeaders(new Metadata)
        if (status.isOk) l.onMessage(Response("done"))
        l.onClose(status, trailers)
      }
    }

    def run(bidi: Boolean, input: ZStream[Any, StatusException, Request]) = SafeMetadata.make.flatMap { headers =>
      if (bidi)
        ClientCalls.withMetadata
          .bidiCall(channel, TestServiceGrpc.METHOD_BIDI_STREAMING, CallOptions.DEFAULT, headers, input)
          .runCollect
          .map(frames => frames.collect { case ResponseFrame.Trailers(_, t) => t }.head)
      else
        ClientCalls.withMetadata
          .clientStreamingCall(channel, TestServiceGrpc.METHOD_CLIENT_STREAMING, CallOptions.DEFAULT, headers, input)
          .map(_.trailers)
    }
  }

  private def transport = for {
    listener <- Promise.make[Nothing, ClientCall.Listener[Response]]
    ready    <- Ref.make[UIO[Boolean]](ZIO.succeed(false))
    checked  <- Queue.unbounded[Unit]
    sent     <- Queue.unbounded[Request]
    half     <- Promise.make[Nothing, Unit]
    canceled <- Ref.make(0)
    init     <- Ref.make[IO[StatusException, Unit]](ZIO.unit)
    finish   <- Ref.make[IO[StatusException, Unit]](ZIO.unit)
  } yield Transport(listener, ready, checked, sent, half, canceled, init, finish)

  private def streamingSuite(bidi: Boolean) = suite(if (bidi) "bidi" else "client streaming")(
    test("waits for readiness and rechecks spurious and repeated notifications") {
      for {
        t     <- transport
        fiber <- t.run(bidi, ZStream(Request(in = 1), Request(in = 2))).fork
        _     <- t.checked.take
        first <- t.sent.size
        _     <- t.onReady
        _     <- t.checked.take
        still <- t.sent.size
        _     <- t.readiness.set(t.readiness.set(ZIO.succeed(false)).as(true))
        _     <- t.onReady
        one   <- t.sent.take
        _     <- t.checked.take.repeatN(1)
        count <- t.sent.size
        _     <- t.readiness.set(ZIO.succeed(true))
        _     <- t.onReady
        two   <- t.sent.take
        _     <- t.halfClosed.await
        _     <- t.close()
        out   <- fiber.join
      } yield assertTrue(
        first == 0,
        still == 0,
        count == 0,
        one.in == 1,
        two.in == 2,
        out.get(trailerKey) == "original trailers"
      )
    },
    test("does not lose onReady delivered during the readiness check") {
      for {
        t     <- transport
        _     <- t.readiness.set(t.readiness.set(ZIO.succeed(true)) *> t.onReady.as(false))
        fiber <- t.run(bidi, ZStream(Request())).fork
        _     <- t.sent.take
        _     <- t.halfClosed.await
        _     <- t.close()
        _     <- fiber.join
      } yield assertTrue(true)
    },
    test("early success stops a sender waiting for readiness") {
      for {
        t     <- transport
        ended <- Promise.make[Nothing, Unit]
        fiber <- t.run(bidi, ZStream(Request()).ensuring(ended.succeed(()))).fork
        _     <- t.checked.take
        _     <- t.close()
        out   <- fiber.join
        _     <- ended.await
        sent  <- t.sent.size
        half  <- t.halfClosed.isDone
      } yield assertTrue(sent == 0, !half, out.get(trailerKey) == "original trailers")
    },
    test("early failure preserves the server status and trailers") {
      for {
        t     <- transport
        fiber <- t.run(bidi, ZStream(Request())).exit.fork
        _     <- t.checked.take
        _     <- t.close(Status.PERMISSION_DENIED.withDescription("server rejection"))
        out   <- fiber.join
        error  = out.causeOption.flatMap(_.failureOption)
      } yield assertTrue(
        error.exists(e =>
          e.getStatus.getCode == Status.Code.PERMISSION_DENIED &&
            e.getStatus.getDescription == "server rejection" && e.getTrailers.get(trailerKey) == "original trailers"
        )
      )
    },
    test("cancellation while waiting interrupts input and cancels the call once") {
      for {
        t     <- transport
        ended <- Promise.make[Nothing, Unit]
        fiber <- t.run(bidi, ZStream(Request()).ensuring(ended.succeed(()))).fork
        _     <- t.checked.take
        _     <- fiber.interrupt
        _     <- ended.await
        n     <- t.cancellations.get
        half  <- t.halfClosed.isDone
      } yield assertTrue(n == 1, !half)
    },
    test("empty input half-closes and waits for the server") {
      for {
        t     <- transport
        fiber <- t.run(bidi, ZStream.empty).fork
        _     <- t.halfClosed.await
        done  <- fiber.poll
        _     <- t.close()
        _     <- fiber.join
        sent  <- t.sent.size
      } yield assertTrue(done.isEmpty, sent == 0)
    },
    test("input failure preserves its exception and cancels the RPC") {
      for {
        t       <- transport
        trailers = new Metadata
        error    = Status.ABORTED.withDescription("input failed").asException(trailers)
        result  <- t.run(bidi, ZStream.fail(error)).exit
        n       <- t.cancellations.get
        half    <- t.halfClosed.isDone
      } yield assertTrue(result.causeOption.flatMap(_.failureOption).contains(error), n == 1, !half)
    },
    test("early completion interrupts blocked input acquisition") {
      for {
        t       <- transport
        entered <- Promise.make[Nothing, Unit]
        ended   <- Promise.make[Nothing, Unit]
        input    = ZStream.fromZIO(entered.succeed(()) *> ZIO.never).ensuring(ended.succeed(()))
        fiber   <- t.run(bidi, input).fork
        _       <- entered.await
        _       <- t.close()
        _       <- fiber.join
        _       <- ended.await
        half    <- t.halfClosed.isDone
      } yield assertTrue(!half)
    },
    test("completion during start waits for initialization and never evaluates the input") {
      for {
        t        <- transport
        release  <- Promise.make[Nothing, Unit]
        closed   <- Promise.make[Nothing, Unit]
        pulled   <- Ref.make(false)
        _        <- t.initialize.set(t.close() *> closed.succeed(()) *> release.await)
        fiber    <- t.run(bidi, ZStream.fromZIO(pulled.set(true).as(Request()))).fork
        _        <- closed.await
        before   <- fiber.poll
        _        <- release.succeed(())
        _        <- fiber.join
        consumed <- pulled.get
        half     <- t.halfClosed.isDone
      } yield assertTrue(before.isEmpty, !consumed, !half)
    },
    test("initialization failure cancels without consuming input") {
      for {
        t      <- transport
        pulled <- Ref.make(false)
        error   = Status.UNAVAILABLE.withDescription("start failed").asException()
        _      <- t.initialize.set(ZIO.fail(error))
        result <- t.run(bidi, ZStream.fromZIO(pulled.set(true).as(Request()))).exit
        read   <- pulled.get
        n      <- t.cancellations.get
      } yield assertTrue(!read, n == 1, result.causeOption.flatMap(_.failureOption).contains(error))
    },
    test("half-close failure propagates and cancels the RPC") {
      for {
        t      <- transport
        error   = Status.INTERNAL.withDescription("half-close failed").asException()
        _      <- t.finish.set(ZIO.fail(error))
        result <- t.run(bidi, ZStream.empty).exit
        n      <- t.cancellations.get
      } yield assertTrue(n == 1, result.causeOption.flatMap(_.failureOption).contains(error))
    },
    test("input defects interrupt the response side and cancel the RPC") {
      for {
        t      <- transport
        defect  = new IllegalStateException("input defect")
        result <- t.run(bidi, ZStream.die(defect)).exit
        n      <- t.cancellations.get
      } yield assertTrue(n == 1, result.causeOption.exists(_.defects.contains(defect)))
    },
    test("early completion stops an infinite ready input") {
      for {
        t     <- transport
        _     <- t.readiness.set(ZIO.succeed(true))
        ended <- Promise.make[Nothing, Unit]
        fiber <- t.run(bidi, ZStream.repeat(Request()).ensuring(ended.succeed(()))).fork
        _     <- t.sent.take
        _     <- t.close()
        _     <- fiber.join
        _     <- ended.await
        half  <- t.halfClosed.isDone
      } yield assertTrue(!half)
    }
  )

  private def bufferedBidi = test("bidi closure stops input before buffered responses are consumed") {
    for {
      t       <- transport
      ended   <- Promise.make[Nothing, Unit]
      reading <- Promise.make[Nothing, Unit]
      release <- Promise.make[Nothing, Unit]
      headers <- SafeMetadata.make
      fiber   <- ClientCalls.withMetadata
                   .bidiCall(
                     t.channel,
                     TestServiceGrpc.METHOD_BIDI_STREAMING,
                     CallOptions.DEFAULT,
                     headers,
                     ZStream(Request()).ensuring(ended.succeed(()))
                   )
                   .mapZIO {
                     case ResponseFrame.Message(_) => reading.succeed(()) *> release.await
                     case _                        => ZIO.unit
                   }
                   .runDrain
                   .fork
      _       <- t.checked.take
      _       <- t.close()
      _       <- reading.await
      _       <- ended.await
      done    <- fiber.poll
      _       <- release.succeed(())
      _       <- fiber.join
    } yield assertTrue(done.isEmpty)
  }

  def spec = suite("request stream backpressure")(streamingSuite(false), streamingSuite(true), bufferedBidi) @@
    TestAspect.timeout(10.seconds)
}
