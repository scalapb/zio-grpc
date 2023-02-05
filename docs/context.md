---
title: Context and Dependencies
sidebar_label: Context and Dependencies
custom_edit_url: https://github.com/scalapb/zio-grpc/edit/master/docs/context.md
---

When implementing a server, ZIO gRPC allows you to specify that your service
methods depend depends on a context of type `Context` which can be any Scala type.

For example, we can define a service with handlers that expect a context of type `User` for each request:

```scala mdoc
import zio.ZIO
import zio.Console
import zio.Console.printLine
import scalapb.zio_grpc.RequestContext
import myexample.testservice.ZioTestservice.ZSimpleService
import myexample.testservice.{Request, Response}
import io.grpc.Status

case class User(name: String)

object MyService extends ZSimpleService[User] {
  def sayHello(req: Request, user: User): ZIO[Any, Status, Response] =
    for {
      _ <- printLine("I am here!").orDie
    } yield Response(s"Hello, ${user.name}")
}
```

## Context transformations

In order to be able to bind our service to a gRPC server, we need to have the
service's Context type to be one of the supported types:
* `scalapb.zio_grpc.RequestContext`
* `scalapb.zio_grpc.SafeMetadata`
* `Any`

The service `MyService` as defined above expects `User` as a context. In order to be able to bind it, we will transform it into a service that depends on a context of type `RequestContext`. To do this, we need to provide the function to produce a `User` out of a `RequestContext`. This way, when a request comes in, ZIO gRPC can take the `RequestContext` (which is request metadata such as headers and options), and use our function to construct a `User` and provide it into the environment of our original service.

In many typical cases, we may need to retrieve the user from a database, and thus we are using an effectful function `RequestContext => IO[Status, User]` to find the user.

For example, we can provide a function that returns an effect that always succeeds:

```scala mdoc
val fixedUserService =
  MyService.transformContextZIO((rc: RequestContext) => ZIO.succeed(User("foo")))
```

and we got our service with context of type `RequestContext` so it can be bound to a gRPC server.

### Accessing metadata

Here is how we would extract a user from a metadata header:
```scala mdoc
import zio.IO
import scalapb.zio_grpc.{ServiceList, ServerMain}

val UserKey = io.grpc.Metadata.Key.of(
  "user-key", io.grpc.Metadata.ASCII_STRING_MARSHALLER)

def findUser(rc: RequestContext): IO[Status, User] =
  rc.metadata.get(UserKey).flatMap {
    case Some(name) => ZIO.succeed(User(name))
    case _          => ZIO.fail(Status.UNAUTHENTICATED.withDescription("No access!"))
  }

val rcService =
  MyService.transformContextZIO(findUser)

object MyServer extends ServerMain {
  def services = ServiceList.add(rcService)
}
```

### Context transformations that depends on a service

A context transformation may introduce a dependency on another service. For example, you
may want to organize your code such that there is a `UserDatabase` service that provides
a `fetchUser` effect that retrieves users from a database. Here is how you can do this:

```scala mdoc
trait UserDatabase {
  def fetchUser(name: String): IO[Status, User]
}

object UserDatabase {
  val layer = zio.ZLayer.succeed(
    new UserDatabase {
      def fetchUser(name: String): IO[Status, User] =
        ZIO.succeed(User(name))
    })
}
```

Now, The context transformation effect we apply may introduce an additional environmental dependency to our service. For example:
```scala mdoc
import zio.Clock._
import zio.Duration._

val myServiceAuthWithDatabase: ZIO[UserDatabase, Nothing, ZSimpleService[RequestContext]] =
  ZIO.serviceWith[UserDatabase](
    userDatabase =>
      MyService.transformContextZIO {
        (rc: RequestContext) =>
            rc.metadata.get(UserKey)
            .someOrFail(Status.UNAUTHENTICATED)
            .flatMap(userDatabase.fetchUser(_))
      }
  )
```

Now our service can be built from an effect that depends on `UserDatabase`. This effect can be
added to a `ServiceList` using `addZIO`:

```scala mdoc
object MyServer2 extends ServerMain {
  def services = ServiceList
    .addZIO(myServiceAuthWithDatabase)
    .provide(UserDatabase.layer)
}
```

## Using a service as ZLayer

If you require more flexibility than provided through `ServerMain`, you can construct
the server directly.

We first turn our service into a ZLayer:

```scala mdoc
val myServiceLayer = zio.ZLayer(myServiceAuthWithDatabase)
```

Notice how the dependencies moved to the input side of the `ZLayer` and the resulting layer is of
type `ZSimpleService[RequestContext]`.

To use this layer in an app, we can wire it like so:

```scala mdoc
import scalapb.zio_grpc.ServerLayer
import scalapb.zio_grpc.Server
import zio.ZLayer

val serviceList = ServiceList
  .addFromEnvironment[ZSimpleService[RequestContext]]

val serverLayer =
    ServerLayer.fromServiceList(
        io.grpc.ServerBuilder.forPort(9000),
        serviceList
    )

val ourApp =
    ZLayer.make[Server](
      serverLayer,
      myServiceLayer,
      UserDatabase.layer
    )

object LayeredApp extends zio.ZIOAppDefault {
    def run = ourApp.launch.exitCode
}
```

`serverLayer` creates a `Server` from a `ZSimpleService` layer and still depends on a `UserDatabase`. Then, `ourApp` feeds a `UserDatabase.layer` into `serverLayer` to produce
a `Server` that doesn't depend on anything. In the `run` method we launch the server layer.

## Implementing a service with dependencies

In this scenario, your service depends on two additional services, `DepA` and `DepB`.  Following [ZIO's service pattern](https://zio.dev/reference/service-pattern/), we accept the (interaces of the ) dependencies as constructor parameters.

```scala mdoc

trait DepA {
  def methodA(param: String): ZIO[Any, Nothing, Int]
}

object DepA {
  val layer = ZLayer.succeed[DepA](new DepA {
    def methodA(param: String) = ???
  })
}

object DepB {
  val layer = ZLayer.succeed[DepB](new DepB {
    def methodB(param: Float) = ???
  })
}

trait DepB {
  def methodB(param: Float): ZIO[Any, Nothing, Double]
}

case class MyService2(depA: DepA, depB: DepB) extends ZSimpleService[User] {
  def sayHello(req: Request, user: User): ZIO[Any, Status, Response] =
    for {
      num1 <- depA.methodA(user.name)
      num2 <- depB.methodB(12.3f)
      _ <- printLine("I am here $num1 $num2!").orDie
    } yield Response(s"Hello, ${user.name}")
}

object MyService2 {
  val layer: ZLayer[DepA with DepB, Nothing, ZSimpleService[RequestContext]] =
    ZLayer.fromFunction {
      (depA: DepA, depB: DepB) =>
        MyService2(depA, depB).transformContextZIO(findUser(_))
  }
}
```

Our service layer now depends on the `DepA` and `DepB` interfaces. A server can be created like this:

```scala mdoc
object MyServer3 extends zio.ZIOAppDefault {

  val serverLayer =
    ServerLayer.fromServiceList(
      io.grpc.ServerBuilder.forPort(9000),
      ServiceList.addFromEnvironment[ZSimpleService[RequestContext]]
    )

  val appLayer = ZLayer.make[Server](
    serverLayer,
    DepA.layer,
    DepB.layer,
    MyService2.layer
  )

  def run = ourApp.launch.exitCode
}
```
