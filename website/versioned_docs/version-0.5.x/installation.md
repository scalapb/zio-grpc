---
title: Installing ZIO gRPC
sidebar_label: Installing
custom_edit_url: https://github.com/scalapb/zio-grpc/edit/master/docs/installation.md
---

## Determining the right version

The version of zio-grpc needs to be compatible with the version of ScalaPB in order to
avoid unintended evictions and ensure binary compatibility:

| ScalaPB   | ZIO gRPC        |
| --------- |-----------------|
| 0.11.x    | 0.5.x           |
| 0.10.x    | 0.4.x           |

## Installation using SBT (Recommended)

If you are building with sbt, add the following to your `project/plugins.sbt`:

```scala
addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.2")

libraryDependencies +=
  "com.thesamet.scalapb.zio-grpc" %% "zio-grpc-codegen" % "0.5.0"
```

Then, add the following lines to your `build.sbt`:

```scala
PB.targets in Compile := Seq(
    scalapb.gen(grpc = true) -> (sourceManaged in Compile).value / "scalapb",
    scalapb.zio_grpc.ZioCodeGenerator -> (sourceManaged in Compile).value / "scalapb"
)

libraryDependencies ++= Seq(
    "io.grpc" % "grpc-netty" % "1.41.0",
    "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion
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
--plugin-artifact=com.thesamet.scalapb.zio-grpc:protoc-gen-zio:0.5.0:default,classifier=unix,ext=sh,type=jar\
-- e2e/src/main/protobuf/service.proto --zio_out=/tmp/out --scala_out=grpc:/tmp/out \
-Ie2e/src/main/protobuf -Ithird_party -Iprotobuf
```

You will need to add to your project the following libraries:
* `com.thesamet.scalapb::scalapb-runtime-grpc:0.11.8`
* `com.thesamet.scalapb.zio-grpc:zio-grpc-core:0.5.0`
* `io.grpc:grpc-netty:1.41.0`
