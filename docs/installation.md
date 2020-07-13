---
title: Installing ZIO gRPC
sidebar_label: Installing
---

## Installation using SBT (Recommended)

If you are building with sbt, add the following to your `project/plugins.sbt`:

```scala
addSbtPlugin("com.thesamet" % "sbt-protoc" % "@sbtProtocVersion@")

libraryDependencies ++=
  "com.thesamet.scalapb.zio-grpc" %% "zio-grpc-codegen" % "@zioGrpcVersion@"
```

Then, add the following line to your `build.sbt`:

```scala
PB.targets in Compile := Seq(
    scalapb.gen() -> (sourceManaged in Compile).value / "scalapb",
    scalapb.zio_grpc.ZioCodeGenerator -> (sourceManaged in Compile).value / "scalapb"
)

libraryDependencies ++= Seq(
    "io.grpc" % "grpc-netty" % "@grpcVersion@"
)
```

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
