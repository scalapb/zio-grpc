package scalapb.zio_grpc.server

import zio._
import io.grpc.ServerCall.Listener
import io.grpc.Status
import zio.stream.Stream
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import zio.stream.ZStream
import scalapb.zio_grpc.RequestContext
import io.grpc.Metadata
import scalapb.zio_grpc.SafeMetadata
import zio.stm.TSemaphore
import zio.Exit.Failure
import zio.Exit.Success

class ZServerCallHandler[Req, Res](
    runtime: Runtime[Any],
    mkListener: (ZServerCall[Res], RequestContext) => UIO[Listener[Req]]
) extends ServerCallHandler[Req, Res] {
  def startCall(
      call: ServerCall[Req, Res],
      headers: Metadata
  ): Listener[Req] = {
    val runner = for {
      responseMetadata <- SafeMetadata.make
      canSend          <- TSemaphore.make(1).commit
      zioCall           = new ZServerCall(call, canSend)
      md               <- SafeMetadata.fromMetadata(headers)
      listener         <- mkListener(zioCall, RequestContext.fromServerCall(md, responseMetadata, call))
    } yield listener

    Unsafe.unsafe { implicit u =>
      runtime.unsafe.run(runner).getOrThrowFiberFailure()
    }
  }
}

object ZServerCallHandler {
  private[zio_grpc] val queueSizeProp = "zio-grpc.backpressure-queue-size"

  val backpressureQueueSize: IO[Status, Int] =
    ZIO
      .attempt(sys.props.get(queueSizeProp).map(_.toInt).getOrElse(16))
      .refineToOrDie[NumberFormatException]
      .catchAll { t =>
        ZIO.fail(Status.INTERNAL.withDescription(s"$queueSizeProp: ${t.getMessage}"))
      }

  def unaryInput[Req, Res](
      runtime: Runtime[Any],
      impl: (Req, RequestContext, ZServerCall[Res]) => ZIO[Any, Status, Unit]
  ): ServerCallHandler[Req, Res] =
    new ZServerCallHandler(runtime, ListenerDriver.makeUnaryInputListener(impl, runtime))

  def streamingInput[Req, Res](
      runtime: Runtime[Any],
      impl: (
          Stream[Status, Req],
          RequestContext,
          ZServerCall[Res]
      ) => ZIO[Any, Status, Unit]
  ): ServerCallHandler[Req, Res] =
    new ZServerCallHandler(
      runtime,
      ListenerDriver.makeStreamingInputListener(impl)
    )

  def unaryCallHandler[Req, Res](
      runtime: Runtime[Any],
      impl: Req => ZIO[RequestContext, Status, Res]
  ): ServerCallHandler[Req, Res] =
    unaryInput[Req, Res](
      runtime,
      (req, requestContext, call) =>
        impl(req).provideEnvironment(ZEnvironment(requestContext)).flatMap[Any, Status, Unit](call.sendMessage)
    )

  def serverStreamingCallHandler[Req, Res](
      runtime: Runtime[Any],
      impl: Req => ZStream[RequestContext, Status, Res]
  ): ServerCallHandler[Req, Res] =
    unaryInput[Req, Res](
      runtime,
      (req: Req, requestContext: RequestContext, call: ZServerCall[Res]) =>
        serverStreamingWithBackpressure(call, impl(req).provideEnvironment(ZEnvironment(requestContext)))
    )

  def clientStreamingCallHandler[Req, Res](
      runtime: Runtime[Any],
      impl: Stream[Status, Req] => ZIO[RequestContext, Status, Res]
  ): ServerCallHandler[Req, Res] =
    streamingInput[Req, Res](
      runtime,
      (req, requestContext, call) =>
        impl(req).provideEnvironment(ZEnvironment(requestContext)).flatMap[Any, Status, Unit](call.sendMessage)
    )

  def bidiCallHandler[Req, Res](
      runtime: Runtime[Any],
      impl: Stream[Status, Req] => ZStream[RequestContext, Status, Res]
  ): ServerCallHandler[Req, Res] =
    streamingInput[Req, Res](
      runtime,
      (req, requestContext, call) =>
        serverStreamingWithBackpressure(call, impl(req).provideEnvironment(ZEnvironment(requestContext)))
    )

  def serverStreamingWithBackpressure[Res](
      call: ZServerCall[Res],
      stream: ZStream[Any, Status, Res]
  ): ZIO[Any, Status, Unit] = {
    def takeFromQueue(queue: Dequeue[Exit[Option[Status], Res]]): ZIO[Any, Status, Unit] =
      queue.takeAll.flatMap(takeFromCache(_, queue))

    def takeFromCache(
        xs: Chunk[Exit[Option[Status], Res]],
        queue: Dequeue[Exit[Option[Status], Res]]
    ): ZIO[Any, Status, Unit] =
      ZIO.succeed {
        var i                          = 0
        var done                       = false
        var loop                       = true
        var error: IO[Status, Nothing] = null
        while (i < xs.length && loop) {
          xs(i) match {
            case Failure(cause) =>
              loop = false
              done = true
              cause.failureOrCause match {
                case Left(Some(status)) =>
                  error = ZIO.fail(status)
                case Left(None)         =>
                case Right(cause)       =>
                  error = ZIO.failCause(cause)
              }
            case Success(value) =>
              call.call.sendMessage(value)
              loop = call.call.isReady
          }
          i += 1
        }

        if (done)
          if (error ne null)
            error
          else
            ZIO.unit
        else if (i < xs.length)
          outerLoop(Some(xs.drop(i)), queue)
        else
          takeFromQueue(queue)
      }.flatten

    def outerLoop(
        cache: Option[Chunk[Exit[Option[Status], Res]]],
        queue: Dequeue[Exit[Option[Status], Res]]
    ): ZIO[Any, Status, Unit] =
      (call.awaitReady *> cache.fold(takeFromQueue(queue))(takeFromCache(_, queue)))

    for {
      queueSize <- backpressureQueueSize
      _         <- ZIO
                     .scoped[Any](
                       stream
                         .toQueueOfElements(queueSize)
                         .flatMap(outerLoop(None, _).fork.flatMap(_.join))
                     )
    } yield ()
  }
}
