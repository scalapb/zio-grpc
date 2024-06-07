package zio_grpc.examples.helloworld

import io.grpc.StatusException
import scalapb.zio_grpc.ServerMain
import scalapb.zio_grpc.ServiceList
import zio._
import zio.Console._

import io.grpc.examples.helloworld.helloworld.ZioHelloworld.Greeter
import io.grpc.examples.helloworld.helloworld.{HelloReply, HelloRequest}

object GreeterImpl extends Greeter {
  def sayHello(
      request: HelloRequest
  ): ZIO[Any, StatusException, HelloReply] =
    printLine(s"Got request: $request").orDie zipRight
      ZIO.succeed(HelloReply(s"Hello, ${request.name}"))
}

object HelloWorldServer extends ServerMain {
  def services: ServiceList[Any] = ServiceList.add(GreeterImpl)
}
