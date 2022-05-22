---
title: Decorating services
description: Transformation of an effect or a stream.
custom_edit_url: https://github.com/scalapb/zio-grpc/edit/master/docs/decorating.md
---

When implementing a server, sometimes you might want to decorate all methods (effects or streams)
in the service, for example to add access and error logging.  

It can be done with the help of `ZTransform`. Instances of this class can be used 
to apply a transformation to all methods of a service to generate a new "decorated" service.
This can be used for pre- or post-processing of requests/responses and also for environment
and context transformations.

We define decoration:

```scala mdoc
import io.grpc.Status
import scalapb.zio_grpc.{ RequestContext, ZTransform }
import zio._
import zio.stream.ZStream

class LoggingTransform[R] extends ZTransform[R, Status, R with RequestContext] {

  def logCause(cause: Cause[Status]): URIO[RequestContext, Unit] = ???

  def accessLog: URIO[RequestContext, Unit] = ???

  override def effect[A](io: ZIO[R, Status, A]): ZIO[R with RequestContext, Status, A] =
    io.zipLeft(accessLog).tapErrorCause(logCause)

  override def stream[A](io: ZStream[R, Status, A]): ZStream[R with RequestContext, Status, A] =
    (io ++ ZStream.fromZIO(accessLog).drain).onError(logCause)
}
```

and then we apply it to our service:

```scala mdoc
import myexample.testservice.ZioTestservice.ZSimpleService
import myexample.testservice.{Request, Response}

object MyService extends ZSimpleService[Any, Any] {
  def sayHello(req: Request): ZIO[Any, Status, Response] =
    ZIO.succeed(Response(s"Hello user"))
}

val decoratedService =
  MyService.transform(new LoggingTransform[Any])
```
