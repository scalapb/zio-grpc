package zio_grpc.examples.helloworld

import io.grpc.Status
import scalapb.zio_grpc.ServerMain
import scalapb.zio_grpc.ServiceList
import zio.{ZEnv, ZIO}
import zio.console._

import io.grpc.examples.helloworld.helloworld.ZioHelloworld.ZGreeter
import io.grpc.examples.helloworld.helloworld.{HelloReply, HelloRequest}

object GreeterImpl extends ZGreeter[ZEnv, Any] {
  def sayHello(
      request: HelloRequest
  ): ZIO[zio.ZEnv, Status, HelloReply] =
    putStrLn(s"Got request: $request") *>
      ZIO.succeed(HelloReply(s"Hello, ${request.name}"))
}

object HelloWorldServer extends ServerMain {
  def services: ServiceList[zio.ZEnv] = ServiceList.add(GreeterImpl)
}
