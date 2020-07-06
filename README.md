[![Snapshot Artifacts][Badge-SonatypeSnapshots]][Link-SonatypeSnapshots]

# zio-grpc

This library enables you to write purely functional [gRPC](https://grpc.io/) services using ZIO.

## Highlights

* Supports all types of RPCs (unary, client streaming, server streaming, bidirectional).
* Uses ZIO's `Stream` to let you easily implement streaming requests.
* Cancellable RPCs: client-side ZIO interruptions are propagated to the server to abort the request and save resources.

## Installation

Find the latest snapshot in [here](https://oss.sonatype.org/content/repositories/snapshots/com/thesamet/scalapb/zio-grpc/zio-grpc-core_2.13/).

Add the following to your `project/plugins.sbt`:

    val zioGrpcVersion = "0.3.0"

    resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

    addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.34")

    libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.10.7"

    libraryDependencies += "com.thesamet.scalapb.zio-grpc" %% "zio-grpc-codegen" % zioGrpcVersion

Add the following to your `build.sbt`:

    val grpcVersion = "1.26.0"
    
    resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

    PB.targets in Compile := Seq(
        scalapb.gen(grpc = true) -> (sourceManaged in Compile).value,
        scalapb.zio_grpc.ZioCodeGenerator -> (sourceManaged in Compile).value,
    )

    libraryDependencies ++= Seq(
        "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion,
        "io.grpc" % "grpc-netty" % grpcVersion
    )

## Usage

Add services under `src/main/protobuf`. For example:

```protobuf
syntax = "proto3";

package examples;

message Request {
    string name = 1;
}

message Response {
    string resp = 1;
}

message Point {
    int32 x = 1;
    int32 y = 2;
}

service MyService {
    rpc Greet(Request) returns (Response);

    rpc Points(Request) returns (stream Point);

    rpc Bidi(stream Point) returns (stream Response);
}
```

This would generate a service like this:
```scala
type MyService = zio.Has[MyServer.Service[Any]]

object MyService {
  trait Service[R] {
    def greet(request: Request): ZIO[R, Status, Response]
    def points(request: Request): ZStream[R, Status, Point]
    def bidi(request: ZStream[Any, Status, Point]): ZStream[R, Status, Response]
  }

  // creates a client service
  def clientService: MyService = ...

  // accessors to use a client provided from the environment:
  def greet(request: Request): ZIO[MyService, Status, Response] = ...
  def points(request: Request): ZStream[MyService, Status, Point] = ...
  def bidi(request: ZStream[Any, Status, Point]): ZStream[MyService, Status, Response] = ...
}
```

See a full example at the [examples directory](https://github.com/scalapb/zio-grpc/tree/master/examples).

[Link-SonatypeSnapshots]: https://oss.sonatype.org/content/repositories/snapshots/com/thesamet/scalapb/zio-grpc/zio-grpc-core_2.13/ "Sonatype Snapshots"
[Badge-SonatypeSnapshots]: https://img.shields.io/nexus/s/https/oss.sonatype.org/com.thesamet.scalapb.zio-grpc/zio-grpc-core_2.13.svg "Sonatype Snapshots"
