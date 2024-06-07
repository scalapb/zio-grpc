---
title: ZIO gRPC and Deadlines
description: Setting deadlines with ZIO gRPC
custom_edit_url: https://github.com/scalapb/zio-grpc/edit/master/docs/deadlines.md
---

When you use a gRPC it is [a very important to set deadlines](https://grpc.io/blog/deadlines/).
In gRPC, deadlines are absolute timestamps that tell our system when the response of an RPC call is
no longer needed. The deadline is sent to the server, and the computation is automatically interrupted
when the deadline is exceeded. The client call automatically ends with a `Status.DEADLINE_EXCEEDED` error.

When you don't specify a deadline, client requests never timeout. All in-flight requests take
resources on the server, and possibly upstream servers, which can ultimately hurt latency or crash
the entire process.

In ZIO gRPC you can easily set deadlines (absolute timestamps), or timeouts which are relative to
the time the outbound call is made.

## Setting timeout for all requests

To set the same timeout for all requests, it is possible to provide a `ClientTransform` when constructing the
client. This transformation is invoked before each request, and can determine the deadline relative to the
system clock at the time the effect is executed.

```scala mdoc
import myexample.testservice.ZioTestservice.ServiceNameClient
import myexample.testservice.{Request, Response}
import scalapb.zio_grpc.{ZManagedChannel, ClientTransform}
import io.grpc.ManagedChannelBuilder
import zio._
import zio.Console._

val channel = ZManagedChannel(
  ManagedChannelBuilder
    .forAddress("localhost", 8980)
    .usePlaintext()
)

// create layer:
val clientLayer = ServiceNameClient.live(
  channel,
  ClientTransform.withTimeoutMillis(3000))

val myAppLogicNeedsEnv = for {
  // use layer through accessor methods:
  res <- ServiceNameClient.unary(Request())
  _ <- printLine(res.toString)
} yield ()
```

## Setting timeout for each request

As in the previous example, assuming there is a client in the environment, we can set the timeout
for each request like this:

```scala mdoc
ServiceNameClient.withTimeoutMillis(3000).unary(Request())
```

Clients provide (through the `GeneratedClient` trait) a number of methods that makes it possible to
specify a deadline or a timeout for each request:

```scala
// Provide a new absolute deadline
def withDeadline(deadline: Deadline): Service

// Sets a new timeout for this service
def withTimeout(duration: zio.duration.Duration): Service

// Sets a new timeout in millis
def withTimeoutMillis(millis: Long): Service

// Replace the call options with the provided call options
def withCallOptions(callOptions: CallOptions): Service

// update the CallOptions for this service
def mapCallOptions(f: CallOptions => CallOptions): Service

// update the request Metadata for this service
def mapMetadataZIO(f: SafeMetadata => UIO[SafeMetadata]): Service
```

If you are using a client instance, the above methods are available to provide you with a new
client that has a modified `CallOptions` effect. Making the copy of those clients is cheap and can
be safely done for each individual call:

```scala mdoc
val clientScoped = ServiceNameClient.scoped(channel)

val myAppLogic = ZIO.scoped {
  clientScoped.flatMap { client =>
    for {
      res <- client
               .withTimeoutMillis(3000).unary(Request())
    } yield res
  }
}
```
