package scalapb.zio_grpc

import io.grpc._
import scalapb.zio_grpc.client.{ClientCalls, ZClientCall}
import scalapb.zio_grpc.testservice.{Request, Response, TestServiceGrpc}
import zio._
import zio.stream.ZStream
import zio.test._

object EarlyClientStreamingResponseSpec extends ZIOSpecDefault {
  private val trailerKey = Metadata.Key.of("early-detail", Metadata.ASCII_STRING_MARSHALLER)

  private def earlyResponse(bidi: Boolean, fail: Boolean, infinite: Boolean) =
    test(
      s"${if (bidi) "bidi" else "client streaming"}: ${if (fail) "failure" else "success"}, ${if (infinite) "infinite"
        else "finite"} input"
    ) {
      ZIO.scoped[Any] {
        for {
          closed     <- Promise.make[Nothing, Unit]
          runtime    <- ZIO.runtime[Any]
          method      = if (bidi) TestServiceGrpc.METHOD_BIDI_STREAMING else TestServiceGrpc.METHOD_CLIENT_STREAMING
          status      = if (fail) Status.RESOURCE_EXHAUSTED.withDescription("early rejection") else Status.OK
          service     = ServerServiceDefinition
                          .builder(method.getServiceName)
                          .addMethod(
                            method,
                            new ServerCallHandler[Request, Response] {
                              override def startCall(
                                  call: ServerCall[Request, Response],
                                  headers: Metadata
                              ): ServerCall.Listener[Request] = {
                                call.request(1)
                                new ServerCall.Listener[Request] {
                                  override def onMessage(request: Request): Unit = {
                                    call.sendHeaders(new Metadata)
                                    if (!fail) call.sendMessage(Response("early success"))
                                    val trailers = new Metadata
                                    trailers.put(trailerKey, "server trailers")
                                    call.close(status, trailers)
                                  }
                                }
                              }
                            }
                          )
                          .build()
          server     <- ZIO.acquireRelease(ZIO.attempt(ServerBuilder.forPort(0).addService(service).build().start()))(s =>
                          ZIO.attempt(s.shutdownNow()).ignore
                        )
          interceptor = new ZClientInterceptor {
                          override def interceptCall[Req, Res](
                              descriptor: MethodDescriptor[Req, Res],
                              options: CallOptions,
                              original: ZClientCall[Req, Res]
                          ): ZClientCall[Req, Res] =
                            new ZClientCall.ForwardingZClientCall[Req, Res](original) {
                              override def start(
                                  listener: ClientCall.Listener[Res],
                                  headers: SafeMetadata
                              ): IO[StatusException, Unit] =
                                original.start(
                                  new ForwardingClientCallListener.SimpleForwardingClientCallListener[Res](listener) {
                                    override def onClose(status: Status, trailers: Metadata): Unit = {
                                      listener.onClose(status, trailers)
                                      Unsafe.unsafe { implicit unsafe =>
                                        runtime.unsafe.run(closed.succeed(())).getOrThrowFiberFailure()
                                        ()
                                      }
                                    }
                                  },
                                  headers
                                )
                            }
                        }
          channel    <- ZChannel.scoped(
                          ManagedChannelBuilder.forAddress("localhost", server.getPort).usePlaintext(),
                          Seq(interceptor),
                          1.second
                        )
          headers    <- SafeMetadata.make
          // The original regression: the second and final request is produced only after
          // the client has processed onClose. Also exercise an unbounded remaining input.
          remaining   = if (infinite) ZStream.repeat(Request(in = 2)) else ZStream(Request(in = 2))
          input       = ZStream(Request(in = 1)) ++ ZStream.fromZIO(closed.await).drain ++ remaining
          result     <- (if (bidi)
                           ClientCalls.withMetadata
                             .bidiCall(channel, method, CallOptions.DEFAULT, headers, input)
                             .runCollect
                             .map(frames =>
                               (
                                 frames.collect { case ResponseFrame.Message(m) => m },
                                 frames.collect { case ResponseFrame.Trailers(_, t) => t }.head
                               )
                             )
                         else
                           ClientCalls.withMetadata
                             .clientStreamingCall(channel, method, CallOptions.DEFAULT, headers, input)
                             .map(r => (Chunk(r.response), r.trailers))).exit.timeout(2.seconds)
        } yield
          if (fail) {
            val error = result.flatMap(_.causeOption).flatMap(_.failureOption)
            assertTrue(
              error.exists(e =>
                e.getStatus.getCode == status.getCode && e.getStatus.getDescription == status.getDescription &&
                  e.getTrailers.get(trailerKey) == "server trailers"
              )
            )
          } else
            assertTrue(result.exists {
              case Exit.Success((responses, trailers)) =>
                responses == Chunk(Response("early success")) && trailers.get(trailerKey) == "server trailers"
              case _                                   => false
            })
      }
    }

  def spec = suite("early streaming response over real gRPC")(
    for {
      bidi     <- Seq(false, true)
      fail     <- Seq(false, true)
      infinite <- Seq(false, true)
    } yield earlyResponse(bidi, fail, infinite)
  ) @@ TestAspect.withLiveClock @@ TestAspect.timeout(10.seconds)
}
