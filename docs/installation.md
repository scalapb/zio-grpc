---
title: Installing ZIO gRPC
sidebar_label: Installing
custom_edit_url: https://github.com/scalapb/zio-grpc/edit/master/docs/installation.md
---

## Installation using SBT (Recommended)

If you are building with sbt, add the following to your `project/plugins.sbt`:

```scala
addSbtPlugin("com.thesamet" % "sbt-protoc" % "@sbtProtocVersion@")

libraryDependencies ++=
  "com.thesamet.scalapb.zio-grpc" %% "zio-grpc-codegen" % "@zioGrpcVersion@"
```

Then, add the following lines to your `build.sbt`:

```scala
PB.targets in Compile := Seq(
    scalapb.gen() -> (sourceManaged in Compile).value / "scalapb",
    scalapb.zio_grpc.ZioCodeGenerator -> (sourceManaged in Compile).value / "scalapb"
)

libraryDependencies ++= Seq(
    "io.grpc" % "grpc-netty" % "@grpcVersion@"
)
```

This configuration will set up the ScalaPB code generator alongside the ZIO gRPC code generator.
Upon compilation, the source generator will process all proto files under `src/main/protobuf`.
The ScalaPB generator will generate case classes for all messages as well as methods to serialize and deserialize those messages. The ZIO gRPC code generator will generate code as described in the [generated code section](generated-code.md).

## Generating code using ScalaPBC (CLI)

:::note See [installation instructions for ScalaPBC](http://scalapb.github.io/scalapbc.html).
:::

If you are using ScalaPBC to generate Scala code from the CLI, you can invoke the zio code generator like this:

```bash
scalapbc \
--plugin-artifact=com.thesamet.scalapb.zio-grpc:protoc-gen-zio:@zioGrpcVersion@:default,classifier=unix,ext=sh,type=jar\
-- e2e/src/main/protobuf/service.proto --zio_out=/tmp/out --scala_out=grpc:/tmp/out \
-Ie2e/src/main/protobuf -Ithird_party -Iprotobuf
```

You will need to add to your project the following libraries:
* `com.thesamet.scalapb::scalapb-runtime-grpc:@scalapbVersion@`
* `com.thesamet.scalapb.zio-grpc:zio-grpc-core:@zioGrpcVersion@`
* `io.grpc:grpc-netty:@grpcVersion@`
