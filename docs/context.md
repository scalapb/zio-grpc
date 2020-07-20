---
title: Context and Dependencies
sidebar_label: Context and Dependencies
---

When implementing a server, ZIO gRPC allows you to specify that your service
depends on an environment of type `R` and a context of type `Context`.

`Context` and `R` can be of any Scala type, however when they are not `Any` they have to be wrapped in an `Has[]`. This allows ZIO gRPC to combine two values (`Context with R`) when providing the values at effect execution time.

For example, we can define a service for which the effects depend on `Console`, and for each request we except to get context of type `User`. Note that `Console` is a type-alias to `Has[Console.Service]` so there is no need wrap it once more in an `Has`.

```scala mdoc
import zio.{Has, ZIO}
import zio.console._
import scalapb.zio_grpc.RequestContext
import myexample.testservice.ZioTestservice.ZSimpleService
import myexample.testservice.{Request, Response}
import io.grpc.Status

case class User(name: String)

object MyService extends ZSimpleService[Console, Has[User]] {
  def sayHello(req: Request): ZIO[Console with Has[User], Status, Response] =
    for {
      user <- ZIO.service[User]
      _ <- putStrLn("I am here!")
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