# Changelog

## [Unreleased - 0.6.0](https://github.com/ScalaPB/zio-grpc/tree/HEAD)

### What changed?

* Removed the `R` type parameter for generated services. Generated services now have a single
  type parameter for context. This simplifies the previous APIs and encourages the ZIO 2
  style that service dependencies are passed via constructors (see [service pattern](https://zio.dev/reference/service-pattern/))
* Context is now received as a second parameter for each handler. For
  convenience, there is a trait that only takes one parameter (the request
  type).
* Simplified ZTransform. Introduce Transform for effectful transformations for
  services that do not have context.
* Classes and methods with suffix ClientWithMetadata have been renamed to ClientWithResponseMetadata.
* Removed the `R` type parameter in most APIs.
* ManagedServer renamed to ScopedServer.
* Clean shutdown when a server is ran through a Scope or ZLayer.
* Added backpressure support [docs](https://scalapb.github.io/zio-grpc/docs/backpressure)
* Added client support for response metadata (#428)
* Signifcant server performance improvement ([2.4x faster handling](https://github.com/scalapb/zio-grpc/pull/457#issuecomment-1350234894)) (#457)
* Server can now send response metadata (#418)
* Clients can now access the response metadata sent by server (#428)
* ServiceList now has a `provideLayer` and `provide` that can be used to
  satisfy services dependencies.
