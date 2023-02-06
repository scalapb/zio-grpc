---
title: Decorating services
description: Transformation of an effect or a stream.
custom_edit_url: https://github.com/scalapb/zio-grpc/edit/master/docs/decorating.md
---

When implementing a server, sometimes you might want to decorate all methods (effects or streams)
in the service, for example to add access and error logging.

It can be done with the help of `ZTransform`. Instances of this class can be used
to apply a transformation to all methods of a service to generate a new "decorated" service.
This can be used for pre- or post-processing of requests/responses and also for context transformations.

We define decoration:

```scala mdoc
import io.grpc.Status
import scalapb.zio_grpc.{ RequestContext, ZTransform }
import zio._
import zio.stream.ZStream

class LoggingTransform extends ZTransform[Any, RequestContext] {

  def logCause(rc: RequestContext, cause: Cause[Status]): UIO[Unit] = ???

  def accessLog(rc: RequestContext): UIO[Unit] = ???

  override def effect[A](io: Any => ZIO[Any, Status, A]): RequestContext => ZIO[Any, Status, A] = {
    rc => io(rc).zipLeft(accessLog(rc)).tapErrorCause(logCause(rc, _))
  }

  override def stream[A](io: Any => ZStream[Any, Status, A]): RequestContext => ZStream[Any, Status, A] = {
    rc => (io(rc) ++ ZStream.fromZIO(accessLog(rc)).drain).onError(logCause(rc, _))
  }
}
```

and then we apply it to our service:

```scala mdoc
import myexample.testservice.ZioTestservice._
import myexample.testservice.{Request, Response}

object MyService extends SimpleService {
  def sayHello(req: Request): ZIO[Any, Status, Response] =
    ZIO.succeed(Response(s"Hello user"))
}

// Note we now have a service with a RequestContext as context.
val decoratedService: ZSimpleService[RequestContext] =
  MyService.transform(new LoggingTransform)
```
