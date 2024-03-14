package examples

import io.grpc.StatusException
import scalapb.zio_grpc.Transform
import zio._
import zio.stream._

object LoggingTransform extends Transform {
  override def effect[A, B](io: A => IO[StatusException, B]): A => IO[StatusException, B] = { a =>
    for {
      _ <- ZIO.log(s"Received request: $a")
      b <- io(a)
      _ <- ZIO.log(s"Responding with: $b")
    } yield b
  }

  override def stream[A, B](io: A => Stream[StatusException, B]): A => Stream[StatusException, B] = { a =>
    val logOutput = (b: B) => ZIO.log(s"Responding with: $b")

    a match {
      case s: Stream[StatusException, Any] @unchecked =>
        val loggedInput = s.tap(r => ZIO.log(s"Received request: $r")).asInstanceOf[A]
        io(loggedInput).tap(logOutput)

      case _ =>
        val logInput = ZIO.log(s"Received request: $a")
        ZStream.fromZIO(logInput).drain ++ io(a).tap(logOutput)
    }
  }
}
