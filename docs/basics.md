---
title: Basics Tutorial
description: A basic tutorial introduction to ZIO gRPC
custom_edit_url: https://github.com/scalapb/zio-grpc/edit/master/docs/basics.md
---

This tutorial provides a basic introduction to Scala programmers to working with ZIO gRPC.

By walking through this example you'll learn how to:

- Define a service in a .proto file.
- Generate server and client code using ZIO gRPC code generator.
- Use ZIO gRPC API to write a simple client and server for your service.

It assumes that you have read the [Introduction to
gRPC](https://grpc.io/docs/what-is-grpc/introduction/) and are familiar with
[protocol buffers](https://developers.google.com/protocol-buffers/docs/overview). Note
that the example in this tutorial uses the
proto3 version of the protocol buffers language: you can find out more in the [proto3 language
guide](https://developers.google.com/protocol-buffers/docs/proto3) and [ScalaPB
generated code
guide](https://scalapb.github.io/generated-code.html).

## Why use gRPC?

Our example is a simple route mapping application that lets clients get information about features on their route, create a summary of their route, and exchange route information such as traffic updates with the server and other clients.

With gRPC we can define our service once in a `.proto` file and generate clients and servers in any of gRPC’s supported languages, which in turn can be run in environments ranging from servers inside a large data center to your own tablet — all the complexity of communication between different languages and environments is handled for you by gRPC. We also get all the advantages of working with protocol buffers, including efficient serialization, a simple IDL, and easy interface updating.

## Example code and setup

The example code for our tutorial is in
[scalapb/zio-grpc/examples/routeguide/src/main/scala/zio_grpc/examples/routeguide](https://github.com/scalapb/zio-grpc/tree/master/examples/routeguide/src/main/scala/zio_grpc/examples/routeguide).
To download the example, clone the latest release in `zio-grpc` repository by
running the following command:

```bash
$ git clone -b v@zioGrpcVersion@ https://github.com/scalapb/zio-grpc.git
```

Then change your current directory to `zio-grpc/examples`:

```bash
$ cd zio-grpc/examples/routeguide
```

## Defining the service

Our first step (as you'll know from the [Introduction to gRPC](https://grpc.io/docs/what-is-grpc/introduction/)) is to
define the gRPC *service* and the method *request* and *response* types using
[protocol buffers](https://developers.google.com/protocol-buffers/docs/overview). You can see the complete .proto file in
[scalapb/zio-grpc/examples/src/main/protobuf/route_guide.proto](https://github.com/scalapb/zio-grpc/blob/master/examples/src/main/protobuf/route_guide.proto).

ZIO gRPC generates code into the same Scala package that ScalaPB uses. Since `java_package` is
specified, the Scala package will be the `java_package` with the proto file name appended to it. In
this case, the package name would be `io.grpc.examples.routeguide.route_guide`.

```protobuf
option java_package = "io.grpc.examples.routeguide";
```

You can read more on how ScalaPB determines the Scala package name and how can this be customized
in [ScalaPB's documentation](https://scalapb.github.io/generated-code.html#default-package-structure).

To define a service, we specify a named `service` in the .proto file:

```protobuf
service RouteGuide {
   ...
}
```

Then we define `rpc` methods inside our service definition, specifying their
request and response types. gRPC lets you define four kinds of service methods,
all of which are used in the `RouteGuide` service:

- A *simple RPC* where the client sends a request to the server and waits for a response to come back.

  ```protobuf
  // Obtains the feature at a given position.
  rpc GetFeature(Point) returns (Feature) {}
  ```

- A *server-side streaming RPC* where the client sends a request to the server
  and gets a stream to read a sequence of messages back. The client reads from
  the returned stream until there are no more messages. As you can see in our
  example, you specify a server-side streaming method by placing the `stream`
  keyword before the *response* type.

  ```protobuf
  // Obtains the Features available within the given Rectangle.  Results are
  // streamed rather than returned at once (e.g. in a response message with a
  // repeated field), as the rectangle may cover a large area and contain a
  // huge number of features.
  rpc ListFeatures(Rectangle) returns (stream Feature) {}
  ```

- A *client-side streaming RPC* where the client sends a stream of messages
  to the server. Once the client has finished writing the messages,
  it waits for the server to read them all and return its response.
  You specify a client-side streaming method by placing the `stream` keyword
   before the *request* type.

  ```protobuf
  // Accepts a stream of Points on a route being traversed, returning a
  // RouteSummary when traversal is completed.
  rpc RecordRoute(stream Point) returns (RouteSummary) {}
  ```

- A *bidirectional streaming RPC* where both sides send a sequence of
messages. The two streams operate independently, so clients and servers can
read and write in whatever order they like: for example, the server could
wait to receive all the client messages before writing its responses, or it
could alternately read a message then write a message, or some other
combination of reads and writes. The order of messages in each stream is
preserved. You specify this type of method by placing the `stream` keyword
before both the request and the response.
  ```protobuf
  // Accepts a stream of RouteNotes sent while a route is being traversed,
  // while receiving other RouteNotes (e.g. from other users).
  rpc RouteChat(stream RouteNote) returns (stream RouteNote) {}
  ```

Our `.proto` file also contains protocol buffer message type definitions for all
the request and response types used in our service methods - for example, here's
the `Point` message type:

```protobuf
// Points are represented as latitude-longitude pairs in the E7 representation
// (degrees multiplied by 10**7 and rounded to the nearest integer).
// Latitudes should be in the range +/- 90 degrees and longitude should be in
// the range +/- 180 degrees (inclusive).
message Point {
  int32 latitude = 1;
  int32 longitude = 2;
}
```

## Generating client and server code

When you compile the application in SBT (using `compile`), an SBT plugin named
`sbt-protoc` invokes two code generators. The first code generator is ScalaPB
which generates case classes for all messages and some gRPC-related code that
ZIO-gRPC interfaces with. The second generator is ZIO gRPC code generator, which
generates a ZIO interface to your service.

The following classes are generated from our service definition in `target/scala_2.13/src_managed`:

- `Feature.scala`, `Point.scala`, `Rectangle.scala`, and others which contain all
  the protocol buffer code to populate, serialize, and retrieve our request and
  response message types.
- `ZioRouteGuide.scala` which contains (along with some other useful code):
  - a base trait for `RouteGuide` servers to implement,
    `ZioRouteGuide.ZRouteGuide`, with all the methods definitions in the
    `RouteGuide` service.
  - `ZioRouteGuide.RouteGuideClient`, contains ZIO accessor methods that clients can
     use to talk to a `RouteGuide` server.

## Creating the server

First let's look at how we create a `RouteGuide` server. If you're only
interested in creating gRPC clients, you can skip this section and go straight
to [Creating the client](#creating-the-client) (though you might find it interesting
anyway!).

There are two parts to making our `RouteGuide` service do its job:

- Implementing the trait `ZRouteGuide` generated from our service definition: returning the
  ZIO effects that do the actual "work" of our service.
- Putting an instance of ZRouteGuide behind a gRPC server to listen for requests from
  clients and return the service responses.

You can find our example `RouteGuide` server in
[scalapb/zio-grpc/examples/src/main/scala/zio_grpc/examples/routeguide/RouteGuideServer.scala](https://github.com/scalapb/zio-grpc/blob/master/examples/src/main/scala/zio_grpc/examples/routeguide/RouteGuideServer.scala).
Let's take a closer look at how it works.

### Implementing ZRouteGuide

As you can see, our server has a `RouteGuideService` class that extends the
generated `ZioRouteGuide.ZRouteGuide` base trait:

```scala
class RouteGuideService(
    features: Seq[Feature],
    routeNotesRef: Ref[Map[Point, List[RouteNote]]]
) extends ZioRouteGuide.ZRouteGuide[ZEnv, Any] {
```

The trait `ZRouteGuide[R, Context]` takes two type parameters:
* `R` represents the environment. These can be dependencies that the server needs in order to do its job. In our example `R` is `ZEnv` which is ZIO's default environment which contains basic services such as `Clock` and `Console`.
* `Context` represents data that is unique to each request, for example, Metadata headers, or the identity of the user making the request. We will learn about `Context` in a future example.

### Simple RPC

`RouteGuideService` implements all our service methods. Let's
look at the simplest method first, `GetFeature()`, which just gets a `Point` from
the client and returns the corresponding feature information from its database
in a `Feature`.
```scala mdoc:passthrough:silent
S.example("routeguide/RouteGuideServer.scala", "getFeature")
S.example("routeguide/RouteGuideServer.scala", "findFeature")
```

The `getFeature()` method takes the request (of type `Point`), and returns a ZIO
effect that represents the work of computing the response. The value that is returned represents
a suspended effect: nothing actually happens until ZIO runtime
ultimately runs the effect. The type of the effect is `ZIO[ZEnv, Status, Feature]` which means
it is a computation:
* can fail with value of type `Status` (this type comes from grpc-java and represents a gRPC status code).
* can succeed with value of type `Feature`.
* requires an environment of type `ZEnv` to run.

In this case, our effect is built on top of a pure function `findFeature` that returns `Some(feature)`
if there is a feature in the database that corresponds to the given point, or `None` otherwise.

We use `ZIO.fromOption` to turn the `Option[Feature]` into an effect of type `IO[Option[Nothing], Feature]`
which means that it can either succeed with a value of type `Feature` or fail with a value of type `Option[Nothing]` (the only possible value of this type is `None` since there are no instances of type `Nothing`). We
then use `mapError` to map the case of an error to gRPC's `NOT_FOUND` status.

### Server-side streaming RPC

Next let's look at one of our streaming RPCs. `ListFeatures` is a server-side
streaming RPC, so we need to send back multiple `Feature`s to our client.

```scala mdoc:passthrough:silent
S.example("routeguide/RouteGuideServer.scala", "listFeatures")
```

Like the simple RPC, this method gets a request object (the `Rectangle` in which
our client wants to find `Feature`s) and returns a `ZStream[ZEnv, Status, Feature]`, which represents an effectful stream that can produce, provided an environment of type
 `ZEnv` zero or more elements of type `Feature` and fail with a value of type of `Status`.

 This time, the stream does not need the environment and can not ever fail (since
 our database is a constant in the same process!)

 We build the stream from a Scala collection we build by filtering through the features
 sequence. ZIO gRPC takes over streaming the response to the client when the stream
 gets executed.

### Client-side streaming RPC

Now let's look at something a little more complicated: the client-side streaming
method `RecordRoute()`, where we get a stream of `Point`s from the client and
return a single `RouteSummary` with information about their trip once the stream finishes.

```scala mdoc:passthrough:silent
S.example("routeguide/RouteGuideServer.scala", "recordRoute")
```

Here, our method gets a stream that is produced by the client. As you can see
from the signature of this method, our goal would be to turn this stream into an
effect that results in a `RouteSummary`.

`RouteSummary` contains the number of points, number of features on the trip, total distance passed, and the time it took. As this summary can be built iteratively we use fold, which takes the summary and new input to compute the next summary. Since we are
adding up the distance between successive pair of points, we will use `zipWithPrevious`
that gives us a pair `(Option[Point], Point)` where the left element represents the previous element in the stream (which is initially None).

The `fold` method gives us a `IO[Status, RouteSummary]`. Using the `timed` method we are getting a new ZIO effect that upon success gives us the a tuple `(zio.duration.Duration, RouteSummary)` where the duration represents the time it took to process
the effect thus far. We then use `map` to turn it back to a `RouteSummary` that contains the elapsed time in seconds.

### Bidirectional streaming RPC

Finally, let's look at our bidirectional streaming RPC `RouteChat()`.

```scala mdoc:passthrough:silent
S.example("routeguide/RouteGuideServer.scala", "routeChat")
```

As with our client-side streaming example, we are getting a `Stream` of
`RouteNote`s, except this time we are also returning a stream of
`RouteNote`s. Although each side will always get the other's messages in the
order they were written, both the client and server can read and write in any
order — the streams operate completely independently.

In this example, we are using `flatMap` on the incoming stream to map each input to a new
effectful stream representing the notes that are available in that location. We are using `Ref#modify` to mutate the collection of notes in the given location and return the list of notes available just prior to the update.

## Starting the server

Once we've implemented all our methods, we also need to start up a gRPC server
so that clients can actually use our service. The following snippet shows how we
do this for our `RouteGuide` service:

```scala mdoc:passthrough:silent
S.example("routeguide/RouteGuideServer.scala", "serverMain")
```

ZIO gRPC provides a base trait to quickly set up gRPC services with zero boilerplate.

1. We override the port we are going to use (default is 9000)
2. Create an effect that constructs an instance of our service (we need an effectful construction since
   our service constructor takes a `zio.Ref`)
3. Override `def services` to return a `ServiceList` that contains our service.

`ServerMain` is meant to be used for simple applications. If you need to do more in your initialization, you can take a look at the source code of `ServerMain` and customize.

## Creating the client

In this section, we'll look at creating a client for our `RouteGuide`
service. You can see our complete example client code in
[RouteGuideClientApp.scala](https://github.com/scalapb/zio-grpc/blob/master/examples/src/main/scala/zio_grpc/examples/routeguide/RouteGuideClientApp.scala).

### Instantiating a client

To call service methods, we first need to create a client. There are two patterns
to work with clients:
- Use `RouteGuideClient.managed` to instantiate a client inside a `zio.ZManaged`. Then through calling its `use` method, the client can be accessed and method can be called on it.
- Use `RouteGuideClient.live` to create a `ZLayer` that can be used to provide a client as a singleton to our program through the environment. In that case, throughout the program we use accessor methods, defined statically in `RouteGuideClient` that expect the client to be available in the environment.

Throughout this tutorial, we will follow the second pattern. We create a `Layer` that can provide a `RouteGuideClient` like this:

```scala
val clientLayer: Layer[Throwable, RouteGuideClient] =
  RouteGuideClient.live(
    ZManagedChannel(
      ManagedChannelBuilder.forAddress("localhost", 8980).usePlaintext()
    )
  )
```

### Calling service methods

Now let's look at how we call our service methods.

As described above, `RouteGuideClient` contains accessor methods for each RPC
that return an effect or a stream that needs a client in the environment to be ran:

```scala
def getFeature(req: Point):
  ZIO[RouteGuideClient, Status, Feature]

def listFeatures(req: Rectangle):
  ZStream[RouteGuideClient, Status, Feature]

def recordRoute[R0](req: ZStream[R0, Status, Point]):
  ZIO[RouteGuideClient with R0, Status, RouteSummary]

def routeChat[R0](req: ZStream[R0, Status, RouteNote]):
  ZStream[RouteGuideClient with R0, Status, RouteNote]
```

### Simple RPC
Calling the simple RPC `GetFeature` on the static accessor stub is as
straightforward as instantiating a local effect:

```scala mdoc:passthrough:silent
S.example("routeguide/RouteGuideClientApp.scala", "getFeature")
```

We create and populate a request protocol buffer object (in our case
`Point`), pass it to the `getFeature()` method on our accessor, and get
back an effect that needs a `RouteGuideClient` environment. We chain
the response with a call to `putStrLn` to print the result on the console,
and we catch the `NOT_FOUND` response and print an error. All other errors
are not handled at this level and will "bubble up" up to the program's `exitCode` handler.

### Server-side streaming RPC

Next, let's look at a server-side streaming call to `ListFeatures`, which
returns a stream of geographical `Feature`s:

```scala mdoc:passthrough:silent
S.example("routeguide/RouteGuideClientApp.scala", "listFeatures")
```

Now `listFeatures` returns a `ZStream`. We use `zipWithIndex` to get a stream
where each of the original elements are accompanied with a zero-based index. We turn
this stream into a single effect that processes the entire stream by calling `foreach`
 and providing it with a function that maps each element into an effect. In this case,
 the effect prints the feature.

### Client-side streaming RPC
Now for something a little more complicated: the client-side streaming method
`RecordRoute`, where we send a stream of `Point`s to the server and get back
a single `RouteSummary`.

```scala mdoc:passthrough:silent
S.example("routeguide/RouteGuideClientApp.scala", "recordRoute")
```

Here, we pass into `recordRoute` an effectful stream that randomly picks an element from the `features` collection (a constant), and insert random delay between elements.

Like all the other accessor methods it's worth noting that no side effect happens upon calling  `recordRoute`. The method returns immediately giving us an effect that represents sending this stream to the server. When the effect ultimately run it can succeed with a value of type `RouteSummary` once the entire stream has been sent to the server.

In this example, we chain to this effect an effect to print the summary to the console.

### Bidirectional streaming RPC
Finally, let's look at our bidirectional streaming RPC `RouteChat()`.

```scala mdoc:passthrough:silent
S.example("routeguide/RouteGuideClientApp.scala", "routeChat")
```

In this method, we both get and return a `Stream` of
RouteNotes. Here both streams execute independently at the same time. Although each side will always
get the other's messages in the order they were written, both the client and
server can read and write in any order — the streams operate completely
independently.

### Providing the client layer into the application logic

All the effects we created were dependent on a `RouteGuideClient` available in the environment. We earlier instantiated a `clientLayer`, so we can provide it to our application logic at the top-level (the `run` method):

```scala mdoc:passthrough:silent
S.example("routeguide/RouteGuideClientApp.scala", "appLogic")
```

## Try it out!

1. Run the server:
   ```bash
   sbt "runMain zio_grpc.examples.routeguide.RouteGuideServer"
   ```

2. From another terminal, run the client:
   ```bash
   sbt "runMain zio_grpc.examples.routeguide.RouteGuideClientApp"
   ```

:::note
This document, "ZIO gRPC: Basics Tutorial", is a derivative of ["gRPC &ndash; Basics Tutorial"](https://grpc.io/docs/languages/java/basics/) by [gRPC Authors](https://grpc.io/), used under [CC-BY-4.0](https://creativecommons.org/licenses/by/4.0). "ZIO gRPC: Basics Tutorial" is licensed under [CC-BY-4.0](https://creativecommons.org/licenses/by/4.0) by Nadav Samet.
:::
