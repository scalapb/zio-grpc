---
title: Quick Start
custom_edit_url: https://github.com/scalapb/zio-grpc/edit/master/docs/quickstart.md
---

This guide gets you started with ZIO gRPC with a simple working example.

## Prerequisites

* [JDK](https://jdk.java.net) version 8 or higher
* [SBT](https://www.scala-sbt.org/)

## Get the example code

The example code is part of the [zio-grpc](https://github.com/scalapb/zio-grpc) repository.

1. [Download the repo as a zip file](https://github.com/scalapb/zio-grpc/archive/v@zioGrpcVersion@.zip) and unzip it, or clone the repo:
   ```bash
   git clone https://github.com/scalapb/zio-grpc
   ```

2. Change to the examples directory:
   ```bash
   cd zio-grpc/examples/helloworld
   ```

## Run the example

From the `examples` directory:

1. Run the server:
   ```bash
   sbt "runMain zio_grpc.examples.helloworld.HelloWorldServer"
   ```

2. From another terminal, run the client:
   ```bash
   sbt "runMain zio_grpc.examples.helloworld.HelloWorldClient"
   ```

Congratulations! You’ve just run a client-server application with ZIO gRPC.

## Update a gRPC service

In this section you’ll update the application by adding an extra server method. The gRPC service is defined using [protocol buffers](https://developers.google.com/protocol-buffers). To learn more about how to define a service in a `.proto` file see [Basics Tutorial](basics.md). For now, all you need to know is that both the server and the client stub have a `SayHello()` RPC method that takes a `HelloRequest` parameter from the client and returns a `HelloReply` from the server, and that the method is defined like this:

```protobuf
// The greeting service definition.
service Greeter {
  // Sends a greeting
  rpc SayHello (HelloRequest) returns (HelloReply) {}
}

// The request message containing the user's name.
message HelloRequest {
  string name = 1;
}

// The response message containing the greetings
message HelloReply {
  string message = 1;
}
```

Open `src/main/protobuf/helloworld.proto` and add a new `SayHelloAgain()` method with the same request and response types as `SayHello()`.

```protobuf
// The greeting service definition.
service Greeter {
  // Sends a greeting
  rpc SayHello (HelloRequest) returns (HelloReply) {}
  // Sends another greeting
  rpc SayHelloAgain (HelloRequest) returns (HelloReply) {}
}

// The request message containing the user's name.
message HelloRequest {
  string name = 1;
}

// The response message containing the greetings
message HelloReply {
  string message = 1;
}
```

Remember to save the file!

## Update the app

The next time we compile the app (using `compile` in sbt), ZIO gRPC will regenerate `ZioHelloworld.scala` which contains a trait with the service definition. The trait has an abstract method for which RPC method. Therefore, with the new method added to the trait, we expect the compilation of `HelloWorldServer.scala` to fail, since the method `sayHelloAgain` will be undefined.

Let's implement the new method in the server and call it from the client.

### Update the server

Open `src/main/scala/zio_grpc/examples/helloworld/HelloWorldServer.scala`, and add the following method to `GreeterImpl`:

```scala
def sayHelloAgain(request: HelloRequest) =
  ZIO.succeed(HelloReply(s"Hello again, ${request.name}"))
```

### Update the client

Open `src/main/scala/zio_grpc/examples/helloworld/HelloWorldClient.scala`, and update the definition of the `myAppLogic` method in `GreeterImpl`:

```scala
def myAppLogic =
  for {
    r <- GreeterClient.sayHello(HelloRequest("World"))
    _ <- putStrLn(r.message)
    s <- GreeterClient.sayHelloAgain(HelloRequest("World"))
    _ <- putStrLn(s.message)
  } yield ()
```

## Run the updated app

If you still have the previous version of the server running, stop it by hitting `Ctrl-C`. Then run the server and client like you did before inside the `examples` directory:

1. Run the server:
   ```bash
   sbt "runMain zio_grpc.examples.helloworld.HelloWorldServer"
   ```

2. From another terminal, run the client:
   ```bash
   sbt "runMain zio_grpc.examples.helloworld.HelloWorldClient"
   ```

## What's next
* Work through the [Basics Tutorial](basics.md).

:::note
This document, "ZIO gRPC: Quick Start", is a derivative of ["gRPC &ndash; Quick Start"](https://grpc.io/docs/languages/java/quickstart/) by [gRPC Authors](https://grpc.io/), used under [CC-BY-4.0](https://creativecommons.org/licenses/by/4.0). "ZIO gRPC: Quick Start" is licensed under [CC-BY-4.0](https://creativecommons.org/licenses/by/4.0) by Nadav Samet.
:::
