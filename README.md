![ZIO gRPC Logo](./website/static/img/zio-grpc-hero.png)

[![Snapshot Artifacts][Badge-SonatypeSnapshots]][Link-SonatypeSnapshots]

# Welcome to ZIO-gRPC

This library enables you to write purely functional [gRPC](https://grpc.io/) services using [ZIO](https://zio.dev).

## Documentation

* [ZIO gRPC homepage](https://scalapb.github.io/zio-grpc)

## Highlights

* Supports all types of RPCs (unary, client streaming, server streaming, bidirectional).
* Uses ZIO's `Stream` to let you easily implement streaming requests.
* Cancellable RPCs: client-side ZIO interruptions are propagated to the server to abort the request and save resources.

[Link-SonatypeSnapshots]: https://oss.sonatype.org/content/repositories/snapshots/com/thesamet/scalapb/zio-grpc/zio-grpc-core_2.13/ "Sonatype Snapshots"
[Badge-SonatypeSnapshots]: https://img.shields.io/nexus/s/https/oss.sonatype.org/com.thesamet.scalapb.zio-grpc/zio-grpc-core_2.13.svg "Sonatype Snapshots"
