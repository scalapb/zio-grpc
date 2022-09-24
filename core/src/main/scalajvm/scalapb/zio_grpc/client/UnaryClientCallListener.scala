package scalapb.zio_grpc.client

import zio.{IO, Promise, Ref, Runtime, Unsafe}
import io.grpc.ClientCall
import io.grpc.{Metadata, Status}
import UnaryCallState._

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
    promise: Promise[Status, (Metadata, Res)]
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

  override def onClose(status: Status, trailers: Metadata): Unit =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run {
          for {
            s <- state.get
            _ <- if (!status.isOk) promise.fail(status)
                 else
                   s match {
                     case ResponseReceived(headers, message) =>
                       promise.succeed((headers, message))
                     case Failure(errorMessage)              =>
                       promise.fail(Status.INTERNAL.withDescription(errorMessage))
                     case _                                  =>
                       promise.fail(
                         Status.INTERNAL.withDescription("No data received")
                       )
                   }
          } yield ()
        }
        .getOrThrowFiberFailure()
    }

  def getValue: IO[Status, (Metadata, Res)] = promise.await
}

object UnaryClientCallListener {
  def make[Res] =
    for {
      runtime <- zio.ZIO.runtime[Any]
      state   <- Ref.make[UnaryCallState[Res]](Initial)
      promise <- Promise.make[Status, (Metadata, Res)]
    } yield new UnaryClientCallListener[Res](runtime, state, promise)
}
