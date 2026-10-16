---
title: Using with Scala.js
sidebar_label: Scala.js
custom_edit_url: https://github.com/scalapb/zio-grpc/edit/master/docs/scala.js.md
---

The [full application example](https://github.com/scalapb/zio-grpc/tree/master/examples/fullapp)
contains a Scala.js client and an Envoy gRPC-web proxy. The browser test suite
covers Scala 2.12, 2.13 and 3.3.

The sbt 2 build uses Scala.js directly, with npm and Webpack for bundling.
Install `grpc-web` in your application's `package.json` (the example and browser
integration tests use version 2.1.1), and configure CommonJS output:

```scala
scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) }
```

Run `fastLinkJS`, then bundle its `main.js` entry point with Webpack. See the
full application's README for the complete commands and its `package-lock.json`
for the tested JavaScript dependency versions.

For sbt 1 applications using sbt-scalajs-bundler, select the runtime explicitly
and override ScalaPB gRPC-web 0.7.0's transitive dependency on grpc-web 1.4.2:

```scala
Compile / npmDependencies += "grpc-web" -> "2.1.1"
Compile / npmResolutions += "grpc-web" -> "2.1.1"
Test / npmResolutions += "grpc-web" -> "2.1.1"
```
