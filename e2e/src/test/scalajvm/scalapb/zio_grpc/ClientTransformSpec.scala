package scalapb.zio_grpc

import zio._
import zio.test._
import zio.test.Assertion._
import io.grpc.CallOptions
import scalapb.zio_grpc.testservice.TestServiceGrpc
import io.grpc.Deadline
import java.util.concurrent.TimeUnit
import io.grpc.Metadata
import io.grpc.StatusException

object ClientTransformSpec extends ZIOSpecDefault {

  val deadline    = Deadline.after(1000, TimeUnit.MILLISECONDS)
  val metadataKey = Metadata.Key.of[String]("test", Metadata.ASCII_STRING_MARSHALLER)

  def run(transform: ClientTransform): ZIO[Any, StatusException, ClientCallContext] =
    for {
      metadata <- SafeMetadata.make
      context  <-
        transform.effect(ZIO.succeed(_))(ClientCallContext(TestServiceGrpc.METHOD_UNARY, CallOptions.DEFAULT, metadata))
    } yield context

  def spec = suite("ClientTransformSpec")(
    test("withDeadline") {
      for {
        contextOut <- run(ClientTransform.withDeadline(deadline))
      } yield assert(contextOut.options.getDeadline())(equalTo(deadline))
    },
    test("withCallOption compose withDeadline") {
      for {
        contextOut <- run(
                        ClientTransform
                          .withCallOptions(CallOptions.DEFAULT)
                          .compose(ClientTransform.withDeadline(deadline))
                      )
      } yield assert(contextOut.options.getDeadline())(equalTo(deadline))
    },
    test("withDeadline andThen withMetadataZIO") {
      for {
        contextOut <- run(
                        ClientTransform
                          .withDeadline(deadline)
                          .andThen(
                            ClientTransform.mapMetadataZIO(metadata => metadata.put(metadataKey, "42").as(metadata))
                          )
                      )
      } yield assert(contextOut.options.getDeadline())(equalTo(deadline)) &&
        assert(contextOut.metadata.metadata.get(metadataKey))(equalTo("42"))
    },
    test("withDeadline compose withMetadataZIO") {
      for {
        contextOut <- run(
                        ClientTransform
                          .withDeadline(deadline)
                          .compose(
                            ClientTransform.withMetadataZIO(
                              SafeMetadata.make.flatMap(metadata => metadata.put(metadataKey, "42").as(metadata))
                            )
                          )
                      )
      } yield assert(contextOut.options.getDeadline())(equalTo(deadline)) &&
        assert(contextOut.metadata.metadata.get(metadataKey))(equalTo("42"))
    }
  )
}
