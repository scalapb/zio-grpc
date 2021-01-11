package scalapb.zio_grpc

import io.grpc.Status
import scalapb.zio_grpc.ZCall.ReadyPromise
import zio.{Promise, Ref, Semaphore, UIO, ZIO}

object ZCall {
  type ReadyPromise = Ref[Option[Promise[Nothing, Unit]]]
}

trait ZCall[-R, A] {
  def request(n: Int): ZIO[R, Status, Unit]

  def sendMessage(message: A): ZIO[R, Status, Unit]

  def sendMessageWhenReady(message: A): ZIO[R, Status, Unit]

  def onReady(): ZIO[R, Status, Unit]
}

trait ZCallNoBackpressure[-R, A] extends ZCall[R, A] {
  def sendMessageWhenReady(message: A): ZIO[R, Status, Unit] =
    sendMessage(message)

  def onReady(): ZIO[R, Status, Unit] =
    ZIO.unit
}

trait ZCallBackpressure[-R, A] extends ZCall[R, A] {
  def sendMessageWhenReady(message: A): ZIO[R, Status, Unit] =
    readySync.withPermit {
      ZIO.ifM(isReady)(
        sendMessage(message),
        Promise.make[Nothing, Unit].flatMap { promise =>
          readyPromise.set(Some(promise)) *> ZIO.ifM(isReady)(
            sendMessage(message),
            promise.await *> sendMessage(message)
          )
        }
      )
    }

  def onReady(): ZIO[R, Status, Unit] =
    readyPromise
      .modify[UIO[Unit]] {
        case Some(promise) => promise.succeed(()).unit -> None
        case None          => ZIO.unit                 -> None
      }
      .flatten
      .whenM(isReady)

  private[zio_grpc] def isReady: ZIO[R, Status, Boolean]

  private[zio_grpc] def readyPromise: ReadyPromise

  private[zio_grpc] def readySync: Semaphore
}

//TODO merge with ZCall after switching to the backpressure supports calls
trait ZCallBase[-R, A] extends ZCallBackpressure[R, A]
