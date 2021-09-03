---
title: Context and Dependencies
sidebar_label: Context and Dependencies
custom_edit_url: https://github.com/scalapb/zio-grpc/edit/master/docs/context.md
---

When implementing a server, ZIO gRPC allows you to specify that your service
depends on an environment of type `R` and a context of type `Context`.

`Context` and `R` can be of any Scala type, however when they are not `Any` they have to be wrapped in an `Has[]`. This allows ZIO gRPC to combine two values (`Context with R`) when providing the values at effect execution time.

For example, we can define a service for which the effects depend on `Console`, and for each request we expect to get a context of type `User`.

```scala mdoc
import zio.{Has, ZIO}
import zio.Console
import zio.Console.printLine
import scalapb.zio_grpc.RequestContext
import myexample.testservice.ZioTestservice.ZSimpleService
import myexample.testservice.{Request, Response}
import io.grpc.Status

case class User(name: String)

object MyService extends ZSimpleService[Has[Console], Has[User]] {
  def sayHello(req: Request): ZIO[Has[Console] with Has[User], Status, Response] =
    for {
      user <- ZIO.service[User]
      _ <- printLine("I am here!").orDie
    } yield Response(s"Hello, ${user.name}")
}
```

As you can see above, we can access both the `User` and the `Console` in our effects. If one of the methods does not need to access the dependencies or context, the returned type from the method can be cleaned up to reflect that certain things are not needed.

## Context transformations

In order to be able to bind our service to a gRPC server, we need to have the
service's Context type to be one of the supported types:
* `Has[scalapb.zio_grpc.RequestContext]`
* `Has[scalapb.zio_grpc.SafeMetadata]`
* `Any`

The service `MyService` as defined above expects `Has[User]` as a context. In order to be able to bind it, we will transform it into a service that depends on a context of type `Has[RequestContext]`. To do this, we need to provide the function to produce a `User` out of a `RequestContext`. This way, when a request comes in, ZIO gRPC can take the `RequestContext` (which is request metadata such as headers and options), and use our function to construct a `User` and provide it into the environment of our original service.

In many typical cases, we may need to retrieve the user from a database, and thus we are using an effectful function `RequestContext => IO[Status, User]` to find the user.

For example, we can provide a function that returns an effect that always succeeds:

```scala mdoc
val fixedUserService =
  MyService.transformContextM((rc: RequestContext) => ZIO.succeed(User("foo")))
```

and we got our service, which still depends on an environment of type `Console`, however the context is now `Has[RequestContext]` so it can be bound to a gRPC server.

### Accessing metadata

Here is how we would extract a user from a metadata header:
```scala mdoc
import zio.IO
import scalapb.zio_grpc.{ServiceList, ServerMain}

val UserKey = io.grpc.Metadata.Key.of(
  "user-key", io.grpc.Metadata.ASCII_STRING_MARSHALLER)

def findUser(rc: RequestContext): IO[Status, User] =
  rc.metadata.get(UserKey).flatMap {
    case Some(name) => IO.succeed(User(name))
    case _          => IO.fail(Status.UNAUTHENTICATED.withDescription("No access!"))
  }

val rcService =
  MyService.transformContextM(findUser)

object MyServer extends ServerMain {
  def services = ServiceList.add(rcService)
}
```

### Depending on a service

A context transformation may introduce a dependency on another service. For example, you
may want to organize your code such that there is a `UserDatabase` service that provides
a `fetchUser` effect that retrieves users from a database. Here is how you can do this:

```scala mdoc
type UserDatabase = Has[UserDatabase.Service]
object UserDatabase {
  trait Service {
    def fetchUser(name: String): IO[Status, User]
  }

  // accessor
  def fetchUser(name: String): ZIO[UserDatabase, Status, User] =
    ZIO.accessZIO[UserDatabase](_.get.fetchUser(name))

  val live = zio.ZLayer.succeed(
    new Service {
      def fetchUser(name: String): IO[Status, User] =
        IO.succeed(User(name))
    })
}
```

Now,
The context transformation effect we apply may introduce an additional environmental dependency to our service. For example:
```scala mdoc
import zio.Clock._
import zio.Duration._

val myServiceAuthWithDatabase  =
  MyService.transformContextM {
    (rc: RequestContext) =>
        rc.metadata.get(UserKey)
        .someOrFail(Status.UNAUTHENTICATED)
        .flatMap(UserDatabase.fetchUser)
  }
```

And now our service not only depends on a `Console`, but also on a `UserDatabase`.

## Using a service as ZLayer
We can turn our service into a ZLayer:

```scala mdoc
val myServiceLive = myServiceAuthWithDatabase.toLayer
```

notice how the dependencies moved to the input side of the `Layer` and the resulting layer is of
type `ZSimpleService[Any, Has[RequestContext]]]`, which means no environment is expected, and it assumes
a `Has[RequestContext]` context. To use this layer in an app, we can wire it like so:

```scala mdoc
import scalapb.zio_grpc.ServerLayer
import zio.ZLayer

val serverLayer =
    ServerLayer.fromServiceLayer(
        io.grpc.ServerBuilder.forPort(9000)
    )(myServiceLive)

val ourApp = (UserDatabase.live ++ Console.any) >>>
    serverLayer

object LayeredApp extends zio.ZIOAppDefault {
    def run = ourApp.build.useForever.exitCode
}
```

`serverLayer` wraps around our service layer to produce a server. Then, `ourApp` layer is constructed such that it takes `UserDatabase.live` in conjuction to a passthrough layer for `Console` to satisfy the two input requirements of `serverLayer`. The outcome, `ourApp`, is a `ZLayer` that can produce a `Server` from a `Console`. In the `run` method we build the layer and run it. Note that we are directly using a `zio.ZioAppDefault` rather than `ServerMain` which does
not support this use case yet.
