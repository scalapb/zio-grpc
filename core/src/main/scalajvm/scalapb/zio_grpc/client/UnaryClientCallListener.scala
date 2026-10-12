package scalapb.zio_grpc.client

import zio.{IO, Promise, Ref, Runtime, UIO, Unsafe}
import io.grpc.{ClientCall, Metadata, Status, StatusException}
import UnaryCallState._
import scalapb.zio_grpc.ResponseContext

sealed trait UnaryCallState[+Res]

object UnaryCallState {
  case object Initial extends UnaryCallState[Nothing]

  case class HeadersReceived[Res](headers: Metadata) extends UnaryCallState[Res]

  case class ResponseReceived[Res](headers: Metadata, message: Res) extends UnaryCallState[Res]

  case class Failure[Res](s: String) extends UnaryCallState[Res]
}

class UnaryClientCallListener[Res](
    runtime: Runtime[Any],
    state: Ref[UnaryCallState[Res]],
    promise: Promise[StatusException, ResponseContext[Res]],
    ready: Ref[Promise[Nothing, Unit]],
    closed: Ref[Boolean]
) extends ClientCall.Listener[Res] {

  override def onHeaders(headers: Metadata): Unit =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          state.update {
            case Initial                => HeadersReceived(headers)
            case HeadersReceived(_)     => Failure("onHeaders already called")
            case ResponseReceived(_, _) => Failure("onHeaders already called")
            case f @ Failure(_)         => f
          }.unit
        )
        .getOrThrowFiberFailure()
    }

  override def onMessage(message: Res): Unit =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          state.update {
            case Initial                  => Failure("onMessage called before onHeaders")
            case HeadersReceived(headers) => ResponseReceived(headers, message)
            case ResponseReceived(_, _)   =>
              Failure("onMessage called more than once for unary call")
            case f @ Failure(_)           => f
          }.unit
        )
        .getOrThrowFiberFailure()
    }

  override def onReady(): Unit =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run {
          Promise.make[Nothing, Unit].flatMap { next =>
            ready.getAndSet(next).flatMap(_.succeed(()))
          }
        }
        .getOrThrowFiberFailure()
    }

  override def onClose(status: Status, trailers: Metadata): Unit =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run {
          for {
            _ <- closed.set(true)
            _ <- ready.get.flatMap(_.succeed(()))
            s <- state.get
            _ <- if (!status.isOk) promise.fail(new StatusException(status, trailers))
                 else
                   s match {
                     case ResponseReceived(headers, message) =>
                       promise.succeed(ResponseContext(headers, message, trailers))
                     case Failure(errorMessage)              =>
                       promise.fail(
                         Status.INTERNAL.withDescription(errorMessage).asException()
                       )
                     case _                                  =>
                       promise.fail(
                         Status.INTERNAL.withDescription("No data received").asException()
                       )
                   }
          } yield ()
        }
        .getOrThrowFiberFailure()
    }

  def getValue: IO[StatusException, ResponseContext[Res]] = promise.await

  def onReadySignal: UIO[Promise[Nothing, Unit]] = ready.get

  def isClosed: UIO[Boolean] = closed.get
}

object UnaryClientCallListener {
  def make[Res] =
    for {
      runtime <- zio.ZIO.runtime[Any]
      state   <- Ref.make[UnaryCallState[Res]](Initial)
      promise <- Promise.make[StatusException, ResponseContext[Res]]
      ready0  <- Promise.make[Nothing, Unit]
      ready   <- Ref.make[Promise[Nothing, Unit]](ready0)
      closed  <- Ref.make(false)
    } yield new UnaryClientCallListener[Res](runtime, state, promise, ready, closed)
}
