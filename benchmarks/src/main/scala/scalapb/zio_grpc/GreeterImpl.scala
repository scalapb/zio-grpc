package scalapb.zio_grpc

import scalapb.zio_grpc.helloworld.testservice.ZioTestservice.Greeter
import scalapb.zio_grpc.helloworld.testservice.{HelloReply, HelloRequest}
import io.grpc.StatusException
import zio.ZIO
import zio.stream.ZStream

class GreeterImpl(size: Long) extends Greeter {

  def sayHello(request: HelloRequest): ZIO[Any, StatusException, HelloReply] =
    ZIO.succeed(HelloReply(request.request))

  def sayHelloStreaming(request: HelloRequest): ZStream[Any, StatusException, HelloReply] =
    ZStream.repeat(HelloReply(request.request)).take(size)

}
