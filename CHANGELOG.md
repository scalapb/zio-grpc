# Changelog

## 0.6.1
* Only buffer stream if queue size is positive (#578, #580)

## 0.6.0

### What changed?

* Backpressure queue size is controlled by either
  `ZIO_GRPC_BACKPRESSURE_QUEUE_SIZE` environment variable or
  `zio_grpc.backpressure_queue_size` system property (previously the names
  were hyphenated). Change was made for compatibility with ZIO Config.
* GeneratedServices no longer have a `R` type parameter for generated services. This simplifies the previous APIs and encourages the ZIO 2
  style that service dependencies are passed via constructors (see [service pattern](https://zio.dev/reference/service-pattern/))
* Added `E` type parameter that represents the error type.
* Context is now received as a second parameter for each handler. For
  convenience, there is a trait that only takes one parameter (the request
  type).
* Introduce Transform for effectful transformations for services that do not have context.
* Introduce GTransform for effectful transformations over both context and error type.
* `ZTransform[ContextIn, ContextOut]` is now a type alias to GTransform that
  fixes the error channel to StatusException.
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
