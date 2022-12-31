---
title: Generated Code Reference
sidebar_label: Generated code
custom_edit_url: https://github.com/scalapb/zio-grpc/edit/master/docs/generated-code.md
---

## Packages and code location

For each proto file that contains services definition, ZIO gRPC generates a Scala
object that will contain service definitions for all services in that file. The
object name would be the proto file name prefixed with `Zio`. It would reside in the same Scala package that ScalaPB will use for definitions in that file.

You can read more on how ScalaPB determines the Scala package name and how can this be customized in [ScalaPB's documentation](https://scalapb.github.io/generated-code.html#default-package-structure).

## Service trait

Inside the object, for each service `ServiceName` that is defined in a `.proto` file, the following structure is generated:

```scala
trait ZServiceName[Context] {
  // methods for each RPC
  def sayHello(request: HelloRequest):
    ZIO[Context, Status, HelloReply]
}
type ServiceName = ZServiceName[Any]
```

The trait `ZServiceName` is to be extended when implementing a server for this service. The trait takes one type parameter `Context`. The `Context` type parameter represents any domain object that you would like your RPC methods to have available in the environment.

You can set `Context` to `Any` when implementing a service to indicate that the service does not have any dependencies or expectations from the environment. Since it is very common situation, especially when getting started, you can have your service implementation extends `ServiceName` which is a type alias to `ZServiceName[Any]`:


```scala
object ServiceNameImpl extends ServiceName {
}
```

Learn more about using [context and dependencies](context.md) in the next section.

:::info
**Why `Any` means that there are no dependencies?** All Scala objects are instances of `Any`. Therefore, any object that is provided as a dependency to our service would satisfy being of type `Any`. In other words, there is no specific instance type required.
:::

### Running the server

The easiest way to run a service, is to create an object that extends `scalapb.zio_grpc.ServerMain`:

```scala
import scalapb.zio_grpc.{ServerMain, ServiceList}

object MyMain extends ServerMain {
  def services = ServiceList.add(ServiceNameImpl)

  // Default port is 9000
  override def port: Int = 8980
}
```

You can also override `def port: Int` to set a port number (by default port 9000 is used).

`ServiceList` contains additional methods to add services to the service list that can be used when the service must be created effectfully, resourcefully (scoped), or provided through a layer.

## Client trait

The generated client follows [ZIO's module pattern](https://zio.dev/docs/howto/howto_use_layers):

```scala
type ServiceNameClient = ServiceNameClient.Service

object ServiceNameClient {
  trait ZService[Context] {
    // methods for use as a client
    def sayHello(request: HelloRequest):
      ZIO[Context, Status, HelloReply]
  }
  type Service = ZService[Any]

  // accessor methods
  def sayHello(request: HelloRequest):
    ZIO[ServiceNameClient, Status, HelloReply]

  def scoped[R](
      managedChannel: ZManagedChannel,
      options: CallOptions =
          io.grpc.CallOptions.DEFAULT,
      headers: zio.UIO[SafeMetadata] =
          scalapb.zio_grpc.SafeMetadata.make
  ): zio.Managed[Throwable, ZService[R]]

  def live[Context](
      managedChannel: ZManagedChannel,
      options: CallOptions =
          io.grpc.CallOptions.DEFAULT,
      headers: zio.UIO[scalapb.zio_grpc.SafeMetadata] =
          scalapb.zio_grpc.SafeMetadata.make
  ): zio.ZLayer[Any, Throwable, ZService[Context]]
}
```

We have two ways to use a client: through a managed resource, or through a layer. In both cases, we start by creating a `ZManagedChannel`, which represents a communication channel to a gRPC server as a managed resource. Since it is scoped, proper shutdown of the channel is guaranteed:

```scala
type ZManagedChannel[R] = ZIO[Scope, Throwable, ZChannel[R]]
```

Creating a channel:
```scala mdoc
import scalapb.zio_grpc.ZManagedChannel
import io.grpc.ManagedChannelBuilder

val channel = ZManagedChannel(
  ManagedChannelBuilder
    .forAddress("localhost", 8980)
    .usePlaintext()
)
```

### Using the client as a layer

A single `ZManagedChannel` represent a virtual connection to a conceptual endpoint to perform RPCs. A channel can have many actual connection to the endpoint. Therefore, it is very common to have a single service client for each RPC service you need to connect to. You can create a `ZLayer` to provide this service using the `live` method on the client companion object. Then simply write your logic using the accessor methods. Finally, inject the layer using `provideLayer` at the top of your app:

```scala mdoc
import myexample.testservice.ZioTestservice.ServiceNameClient
import myexample.testservice.{Request, Response}
import zio._
import zio.Console._

// create layer:
val clientLayer = ServiceNameClient.live(channel)

val myAppLogicNeedsEnv = for {
  // use layer through accessor methods:
  res <- ServiceNameClient.unary(Request())
  _ <- printLine(res.toString)
} yield ()

// myAppLogicNeedsEnv needs access to a ServiceNameClient. We turn it into
// a self-contained effect (IO) by providing the layer to it:
val myAppLogic1 = myAppLogicNeedsEnv.provideLayer(clientLayer)

object LayeredApp extends zio.ZIOAppDefault {
  def run: UIO[ExitCode] = myAppLogic1.exitCode
}
```

Here the application is broken to multiple value assignments so you can see the types.
The first effect `myAppLogicNeedsEnv` uses accessor functions, which makes it depend on  an environment of type `ServiceNameClient`. It chains the `unary` RPC with printing the result to the console, and hence the final inferred effect type is `ServiceNameClient`. Once we provide our custom layer, the effect type is `ZEnv`, which we can use with ZIO's `exit` method.

### Using a Scoped client

As an alternative to using ZLayer, you can use the client as a scoped resource:

```scala mdoc
import myexample.testservice.ZioTestservice.ServiceNameClient
import myexample.testservice.{Request, Response}

val clientManaged = ServiceNameClient.scoped(channel)

val myAppLogic = ZIO.scoped {
  clientManaged.flatMap { client =>
    for {
      res <- client.unary(Request()).mapError(_.asRuntimeException)
    } yield res
  }
}
```

Since the service acquistion can fail with a `Throwable`, and the RPC effects of ZIO gRPC can fail with `Status` (which is not a subtype of `Throwable`), we use `mapError` to map the RPC error to a `StatusRuntimeException`. This way, the resulting effect can fail with a `Throwable`.
