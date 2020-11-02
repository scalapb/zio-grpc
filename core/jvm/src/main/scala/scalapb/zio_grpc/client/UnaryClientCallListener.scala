package scalapb.zio_grpc.client

import zio.{IO, Promise, Ref, Runtime, URIO}
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

class UnaryClientCallListener[R, Res](
    runtime: Runtime[R],
    call: ZClientCall[R, _, Res],
    state: Ref[UnaryCallState[Res]],
    promise: Promise[Status, (Metadata, Res)]
) extends ClientCall.Listener[Res] {

  override def onHeaders(headers: Metadata): Unit =
    runtime.unsafeRun(
      state
        .update({
          case Initial                => HeadersReceived(headers)
          case HeadersReceived(_)     => Failure("onHeaders already called")
          case ResponseReceived(_, _) => Failure("onHeaders already called")
          case f @ Failure(_)         => f
        })
        .unit
    )

  override def onMessage(message: Res): Unit =
    runtime.unsafeRun(
      state
        .update({
          case Initial                  => Failure("onMessage called before onHeaders")
          case HeadersReceived(headers) => ResponseReceived(headers, message)
          case ResponseReceived(_, _)   =>
            Failure("onMessage called more than once for unary call")
          case f @ Failure(_)           => f
        })
        .unit
    )

  override def onClose(status: Status, trailers: Metadata): Unit =
    runtime.unsafeRun {
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

  override def onReady(): Unit =
    runtime.unsafeRun(call.onReady())

  def getValue: IO[Status, (Metadata, Res)] = promise.await
}

object UnaryClientCallListener {
  def make[R, Res](
      call: ZClientCall[R, _, Res]
  ): URIO[R, UnaryClientCallListener[R, Res]] =
    for {
      runtime <- zio.ZIO.runtime[R]
      state   <- Ref.make[UnaryCallState[Res]](Initial)
      promise <- Promise.make[Status, (Metadata, Res)]
    } yield new UnaryClientCallListener[R, Res](runtime, call, state, promise)
}
