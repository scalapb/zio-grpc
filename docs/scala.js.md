---
title: Using with Scala.js
sidebar_label: Scala.js
custom_edit_url: https://github.com/scalapb/zio-grpc/edit/master/docs/scala.js.md
---

The [full application example](https://github.com/scalapb/zio-grpc/tree/master/examples/fullapp)
contains a Scala.js client and an Envoy gRPC-web proxy. The browser test suite
covers Scala 2.12, 2.13 and 3.3.

With sbt-scalajs-bundler, select the tested gRPC-web runtime explicitly:

```scala
Compile / npmDependencies += "grpc-web" -> "2.1.1"
Compile / npmResolutions += "grpc-web" -> "2.1.1"
Test / npmResolutions += "grpc-web" -> "2.1.1"
```

The resolution is necessary because ScalaPB's gRPC-web facade 0.7.0 declares
an exact dependency on grpc-web 1.4.2. ZIO-gRPC retains a compatible published
version range so existing consumers can still resolve their dependencies;
the example and browser integration tests exercise grpc-web 2.1.1.
