---
title: Introduction
custom_edit_url: https://github.com/scalapb/zio-grpc/edit/master/docs/intro.md
---

ZIO-gRPC lets you write purely functional gRPC servers and clients. It is built on top of [ZIO](https://zio.dev/), a library for asynchronous and concurrent functional programming in Scala.

## Highlights
* Supports all types of RPCs (unary, client streaming, server streaming, bidirectional).
* Cancellable RPCs: easily cancel RPCs by calling `interrupt` on the effect. Server will immediately abort execution.
* Scala.js support: call your service from Scala code running on the browser

## Why ZIO gRPC?

One of the advantages of a microservice architecture is the ability to write different microservices using different technogies. ZIO gRPC might be a great choice for your project if you value:
* **Type-safety**: your business logic and the data types are checked at compile time.
* **Resource safety**: managed resources (such as database connectins) are guanteed to be released.
* **Reusable behaviors**: Creating complex behaviors by easily combining basic building blocks. For example:
  * Exponential backoff, is a `retry` method call that gets an exponential schedule as a parameter.
  * Sending a few identical requests to a number of servers and wait only until the first response.
  * Sending different requests in parallel and collecting all the results as a list.
* **Living on the edge**: Yes, this is a word of warning. Both ZIO and ZIO gRPC are new technologies. While a lot of effort has been put to test, it is possible that you will encounter bugs. For ZIO gRPC, APIs may change between minor releases without notice.

## Effects as pure values

In ZIO gRPC, the services you will write will be purely functional. When a client makes an RPC call to your service, an "handler" method in your service will be invoked. In contrast to imperative programming, instead of actually handling the call, this handler method will return a pure immutable value of type `ZIO`. This value, on its own, doesn't do anything - it represents the work that needs to get done to fulfill the request, for example:  reading from a database, making a network call, or calling a local function. ZIO's runtime is going to run the effect immediately after you return it. As you will see, structuring your program by combining functional effects will lead to reusable code that is easier to reason about and more likely to be correct once you get it to compile.

There are also technical advantages: in case the client aborts the request, ZIO gRPC can interrupt the server computation even if the server is executing an effect that is unrelated to ZIO gRPC (in grpc-java for example, this can only be accomplished by the server occassionally checking for a cancellation). Using ZIO building blocks such as `ZIO.bracket`, `ZIO#onExit`, `ZIO.uninterruptible` you remain in control over the behavior of the program in case of interruptions.

# Try it out

* Got 5 to 10 more minutes? Check out our [Quick Start tutorial](quickstart.md). You will clone an existing ZIO gRPC client and a server. You will run them and add a new RPC method.
* Got up to an hour? Take a look at the [Basics tutorial](basics.md). You will learn how to implement gRPC servers and clients, including all sort of streaming requests available in gRPC. The tutorial will also show you how to hook the clients and servers into a full working ZIO application.