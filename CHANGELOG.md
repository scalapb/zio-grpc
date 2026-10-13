# Changelog

Notable changes to ZIO gRPC, reconstructed from the repository's Git tags and
commit history. Releases are listed in descending version order, including test
releases and release candidates. Dates are UTC dates from tag metadata (or the
associated commit for lightweight tags), not verified artifact publication dates.
Routine build, CI, and documentation dependency updates are omitted unless noted;
the linked diffs contain the complete history. The 0.5.x line uses ZIO 1; 0.6.x uses ZIO 2.

Add changes for the next release under **Unreleased**.

## [Unreleased](https://github.com/scalapb/zio-grpc/compare/v0.6.3...master)

### Added

* Respect gRPC readiness when sending client-streaming and bidirectional requests; stop the request stream when the server closes the call, including early responses ([#729](https://github.com/scalapb/zio-grpc/issues/729)).

### Changed

* Upgrade ZIO from 2.0.22 to 2.1.26 ([#671](https://github.com/scalapb/zio-grpc/issues/671), [#720](https://github.com/scalapb/zio-grpc/issues/720)).
* Update gRPC to 1.84.2 and ScalaPB to 0.11.21 ([#742](https://github.com/scalapb/zio-grpc/issues/742), [#729](https://github.com/scalapb/zio-grpc/issues/729)).
* Update Scala to 2.12.21 / 2.13.18 / 3.3.8 and Scala.js to 1.22.0; keep Scala 3 on the 3.3 LTS line ([#715](https://github.com/scalapb/zio-grpc/issues/715), [#713](https://github.com/scalapb/zio-grpc/issues/713), [#721](https://github.com/scalapb/zio-grpc/issues/721), [#723](https://github.com/scalapb/zio-grpc/issues/723), [#743](https://github.com/scalapb/zio-grpc/issues/743)).
* Modernize build and browser tooling, pin the Nix development environment, and validate examples against the local checkout in CI ([#733](https://github.com/scalapb/zio-grpc/issues/733)).
* Migrate publishing to Central Portal and gate releases on CI for the exact commit. Deploy documentation separately through GitHub Pages Actions ([#733](https://github.com/scalapb/zio-grpc/issues/733), [#751](https://github.com/scalapb/zio-grpc/issues/751)).

### Documentation

* Migrate the documentation website to Docusaurus 3 and refresh its dependencies ([#733](https://github.com/scalapb/zio-grpc/issues/733)).
* Expand this changelog back to the library's inception, including prereleases and release comparison links ([#674](https://github.com/scalapb/zio-grpc/issues/674)).

## [0.6.3](https://github.com/scalapb/zio-grpc/compare/v0.6.2...v0.6.3) - 2024-10-30

### Added

* Streaming-client backpressure with a configurable batch size ([#636](https://github.com/scalapb/zio-grpc/issues/636)).

### Changed

* Delay sending response headers until the first message is ready ([#635](https://github.com/scalapb/zio-grpc/issues/635)).
* Update gRPC to 1.64.0, Scala to 2.12.20 / 2.13.15 / 3.3.4, and Scala.js to 1.17.0.

### Fixed

* Close client calls when they finish without failure or interruption, fixing a connection leak ([#648](https://github.com/scalapb/zio-grpc/issues/648)).

## [0.6.2](https://github.com/scalapb/zio-grpc/compare/v0.6.1...v0.6.2) - 2024-05-07

### Changed

* Optimize client calls ([#618](https://github.com/scalapb/zio-grpc/issues/618)).
* Stop emitting `private[this]` in generated code for compatibility with newer Scala versions ([#615](https://github.com/scalapb/zio-grpc/issues/615)).
* Update ZIO to 2.0.22, gRPC to 1.63.0, and Scala 3 to 3.3.3.

### Fixed

* Avoid blocking threads when interrupting fibers ([#612](https://github.com/scalapb/zio-grpc/issues/612)).
* Suspend metadata allocation in `SafeMetadata.make` with key/value pairs so each execution creates fresh metadata ([#614](https://github.com/scalapb/zio-grpc/issues/614)).

## [0.6.1](https://github.com/scalapb/zio-grpc/compare/v0.6.0...v0.6.1) - 2024-01-10

### Fixed

* Only buffer streams when the backpressure queue size is positive; document how to disable buffering ([#578](https://github.com/scalapb/zio-grpc/issues/578), [#580](https://github.com/scalapb/zio-grpc/issues/580)).

### Changed

* Update ZIO to 2.0.19, ScalaPB to 0.11.14, gRPC to 1.60.0, and Scala 3 to 3.3.1.

## [0.6.0](https://github.com/scalapb/zio-grpc/compare/v0.5.1...v0.6.0) - 2023-08-11

First stable ZIO 2 release. This summary covers the 0.6.0 prerelease cycle;
individual prerelease changes are listed below. The full diff starts at 0.5.1,
the last stable release before the ZIO 1 and ZIO 2 development lines diverged.

### Breaking changes

* Migrate from ZIO 1 to ZIO 2; this release uses ZIO 2.0.15 ([#299](https://github.com/scalapb/zio-grpc/issues/299)).
* Remove the `R` environment parameter from generated services and most runtime APIs. Pass service dependencies through constructors.
* Add an `E` error type parameter to generated services. Use `StatusException` instead of `Status` for gRPC errors ([#466](https://github.com/scalapb/zio-grpc/issues/466)).
* Pass context as the second parameter of each service handler. A convenience trait accepts only the request parameter.
* Introduce `Transform` for effectful transformations without context and `GTransform` for transformations of context and error types. `ZTransform` is an alias for `GTransform` with the error type fixed to `StatusException`.
* Rename `ClientWithMetadata` APIs to `ClientWithResponseMetadata`.
* Rename `ManagedServer` to `ScopedServer`; remove methods deprecated in 0.4.0.
* Rename `ServiceList.access` to `addFromEnvironment` and remove `ServiceList.addLayer`. Use `provideLayer` or `provide` to supply service dependencies.
* Rename effectful methods to follow ZIO 2 conventions, such as `ServiceList.addZIO`.
* Configure backpressure through `ZIO_GRPC_BACKPRESSURE_QUEUE_SIZE` or `zio_grpc.backpressure_queue_size`. These replace the hyphenated names used earlier in the prerelease cycle.

### Added

* Server response metadata/trailers and client access to response metadata ([#411](https://github.com/scalapb/zio-grpc/issues/411), [#430](https://github.com/scalapb/zio-grpc/issues/430)).
* Streaming backpressure, with subsequent performance improvements ([#448](https://github.com/scalapb/zio-grpc/issues/448), [#468](https://github.com/scalapb/zio-grpc/issues/468), [#478](https://github.com/scalapb/zio-grpc/issues/478), [#514](https://github.com/scalapb/zio-grpc/issues/514)). See the [backpressure documentation](https://scalapb.github.io/zio-grpc/docs/backpressure).
* Scala 3 support on the JVM and Scala.js, including client-provided metadata on Scala.js ([#422](https://github.com/scalapb/zio-grpc/issues/422)).
* Transformations for client traits, including client-streaming transformations and applying `ZTransform` to service clients ([#528](https://github.com/scalapb/zio-grpc/issues/528)).
* `SafeMetadata.wrapM` and a less restrictive type for `ServiceList.addManaged` before its ZIO 2 rename ([#303](https://github.com/scalapb/zio-grpc/issues/303), [#301](https://github.com/scalapb/zio-grpc/issues/301)).

### Fixed

* Shut down servers cleanly when their scope or layer closes, and wait for termination on shutdown ([#416](https://github.com/scalapb/zio-grpc/issues/416), [#420](https://github.com/scalapb/zio-grpc/issues/420), [#522](https://github.com/scalapb/zio-grpc/issues/522)).
* Improve server performance by removing calls to `race` ([#457](https://github.com/scalapb/zio-grpc/issues/457)); the original benchmark reported [2.4× faster handling](https://github.com/scalapb/zio-grpc/pull/457#issuecomment-1350234894).
* Revert the call-fiber cleanup change introduced in rc6 ([#539](https://github.com/scalapb/zio-grpc/issues/539)).

## [0.6.0-rc6](https://github.com/scalapb/zio-grpc/compare/v0.6.0-rc5...v0.6.0-rc6) - 2023-07-15

* Allow applying `ZTransform` to service clients ([#528](https://github.com/scalapb/zio-grpc/issues/528)).
* Replace the backpressure implementation with a channel-based implementation ([#514](https://github.com/scalapb/zio-grpc/issues/514)).
* Wait for server termination on shutdown ([#522](https://github.com/scalapb/zio-grpc/issues/522)).
* Add call-fiber cleanup on exit ([#515](https://github.com/scalapb/zio-grpc/issues/515); reverted before 0.6.0 final in [#539](https://github.com/scalapb/zio-grpc/issues/539)).
* Fix Java 8 compatibility and add Scala.js end-to-end tests.
* **Breaking:** add an `E` error type parameter to generated services and introduce `GTransform` for context and error transformations. Make `ZTransform` a specialized alias; document mapping service error types ([#527](https://github.com/scalapb/zio-grpc/issues/527)).
* Update ZIO to 2.0.15 and Scala 3 to 3.3.0.

## [0.6.0-rc5](https://github.com/scalapb/zio-grpc/compare/v0.6.0-rc4...v0.6.0-rc5) - 2023-04-04

* Support client-provided metadata on Scala.js.
* Use ZIO Config to read backpressure settings; rename the environment variable to `ZIO_GRPC_BACKPRESSURE_QUEUE_SIZE` and the system property to `zio_grpc.backpressure_queue_size`.

## [0.6.0-rc4](https://github.com/scalapb/zio-grpc/compare/v0.6.0-rc3...v0.6.0-rc4) - 2023-03-28

* **Breaking:** replace `Status` errors with `StatusException` ([#466](https://github.com/scalapb/zio-grpc/issues/466)).
* Fix the Scala.js client implementation for the updated APIs.

## [0.6.0-rc3](https://github.com/scalapb/zio-grpc/compare/v0.6.0-rc2...v0.6.0-rc3) - 2023-03-05

* Enable Scala 3 support for Scala.js.
* Update ZIO to 2.0.9 and Scala.js to 1.13.0.

## [0.6.0-rc2](https://github.com/scalapb/zio-grpc/compare/v0.6.0-rc1...v0.6.0-rc2) - 2023-02-15

* Restore `andThen` composition for transforms.
* Implement client-streaming transforms.
* Optimize the backpressure loop ([#478](https://github.com/scalapb/zio-grpc/issues/478)).
* Update ZIO to 2.0.8 and ScalaPB to 0.11.13.

## [0.6.0-rc1](https://github.com/scalapb/zio-grpc/compare/v0.6.0-test8...v0.6.0-rc1) - 2023-02-05

* **Breaking:** move service context to a second handler parameter and add a convenience service trait without context.
* Introduce `Transform` for services without context and simplify `ZTransform`.
* **Breaking:** rename `ClientWithMetadata` APIs to `ClientWithResponseMetadata`.
* Add documentation and an example for supplying dependencies through layers.

## [0.6.0-test8](https://github.com/scalapb/zio-grpc/compare/v0.6.0-test7...v0.6.0-test8) - 2023-01-19

* Fix a significant server-streaming performance regression involving `stream.toQueue` ([#468](https://github.com/scalapb/zio-grpc/issues/468)).

## [0.6.0-test7](https://github.com/scalapb/zio-grpc/compare/v0.6.0-test6...v0.6.0-test7) - 2023-01-01

* **Breaking:** remove `ServiceList.addLayer` and rename `ServiceList.access` to `addFromEnvironment`.

## [0.6.0-test6](https://github.com/scalapb/zio-grpc/compare/v0.6.0-test5...v0.6.0-test6) - 2022-12-31

* **Breaking:** remove the environment type parameter from generated services and most runtime APIs.
* **Breaking:** rename `ManagedServer` to `ScopedServer`; remove methods deprecated in 0.4.0.
* Add streaming backpressure for ZIO 2 ([#448](https://github.com/scalapb/zio-grpc/issues/448)).
* Improve server performance by removing calls to `race` ([#457](https://github.com/scalapb/zio-grpc/issues/457)).
* Expand `ServiceList` construction and dependency-provisioning APIs.
* Update ZIO to 2.0.5.

## [0.6.0-test5](https://github.com/scalapb/zio-grpc/compare/v0.6.0-test4...v0.6.0-test5) - 2022-11-24

* Add client access to response metadata ([#430](https://github.com/scalapb/zio-grpc/issues/430)).
* Cleanly shut down servers when their scope or layer closes ([#416](https://github.com/scalapb/zio-grpc/issues/416)).
* Fix shutdown timeout conversion to milliseconds ([#420](https://github.com/scalapb/zio-grpc/issues/420)).
* Enable the Scala 3 JVM build ([#422](https://github.com/scalapb/zio-grpc/issues/422)).
* Update ZIO to 2.0.4 and ScalaPB to 0.11.12.

## [0.6.0-test4](https://github.com/scalapb/zio-grpc/compare/v0.6.0-test3...v0.6.0-test4) - 2022-07-22

* Add server response metadata/trailers ([#411](https://github.com/scalapb/zio-grpc/issues/411)).
* Update to ZIO 2.0.0 and ScalaPB 0.11.11 ([#400](https://github.com/scalapb/zio-grpc/issues/400), [#401](https://github.com/scalapb/zio-grpc/issues/401)).

## [0.6.0-test3](https://github.com/scalapb/zio-grpc/compare/v0.6.0-test2...v0.6.0-test3) - 2022-05-25

* **Breaking:** rename effectful methods to follow ZIO 2 conventions.
* Update the documentation and examples for ZIO 2; preserve versioned documentation for 0.5.x.

## [0.6.0-test2](https://github.com/scalapb/zio-grpc/compare/v0.6.0-test1...v0.6.0-test2) - 2022-05-14

* Update ZIO to 2.0.0-RC6.
* Update the examples to ZIO 2 and restore example builds in CI.

## [0.6.0-test1](https://github.com/scalapb/zio-grpc/compare/v0.5.1...v0.6.0-test1) - 2022-01-12

* **Breaking:** begin the migration to ZIO 2 using 2.0.0-RC1 ([#299](https://github.com/scalapb/zio-grpc/issues/299)).
* Add initial transform support for client traits.
* Add `SafeMetadata.wrapM` ([#303](https://github.com/scalapb/zio-grpc/issues/303)).
* Relax the type required by `ServiceList.addManaged` ([#301](https://github.com/scalapb/zio-grpc/issues/301)).
* Update ScalaPB to 0.11.8.

## [0.5.3](https://github.com/scalapb/zio-grpc/compare/v0.5.2...v0.5.3) - 2022-12-10

ZIO 1 maintenance release, developed alongside the 0.6.0 prereleases.

* Add streaming backpressure and configurable queue size through environment variables or system properties ([#448](https://github.com/scalapb/zio-grpc/issues/448), [#456](https://github.com/scalapb/zio-grpc/issues/456)).
* Add client access to response metadata ([#428](https://github.com/scalapb/zio-grpc/issues/428)).
* Shut down servers cleanly when managed resources are released ([#419](https://github.com/scalapb/zio-grpc/issues/419)).

## [0.5.2](https://github.com/scalapb/zio-grpc/compare/v0.5.1...v0.5.2) - 2022-08-05

ZIO 1 maintenance release, developed alongside the 0.6.0 prereleases.

* Add server response metadata/trailers ([#418](https://github.com/scalapb/zio-grpc/issues/418)).
* Add initial transform support for client traits.
* Add `SafeMetadata.wrapM` to wrap effectfully obtained metadata ([#303](https://github.com/scalapb/zio-grpc/issues/303)).
* Relax the type required by `ServiceList.addManaged` ([#301](https://github.com/scalapb/zio-grpc/issues/301)).
* Update ZIO to 1.0.13, ScalaPB to 0.11.8, and gRPC to 1.41.0.

## [0.5.1](https://github.com/scalapb/zio-grpc/compare/v0.5.0...v0.5.1) - 2021-08-07

* **Breaking:** remove `ZService.withMetadata` because it cannot be used safely ([#283](https://github.com/scalapb/zio-grpc/issues/283)).
* Add variance to `ZTransform`.
* Remove the unnecessary `scala-collection-compat` dependency.
* Update ZIO to 1.0.10 and gRPC to 1.39.0.
* Disable Scala 3 builds pending working support.

## [0.5.0](https://github.com/scalapb/zio-grpc/compare/v0.4.4...v0.5.0) - 2021-03-31

* Upgrade to ScalaPB 0.11.0 and scalapb-grpcweb 0.6.1.
* Prioritize failures over interruptions when converting call exits to gRPC statuses ([#225](https://github.com/scalapb/zio-grpc/issues/225)).
* Fix Scala 3 compilation issues.
* Update ZIO to 1.0.5 and gRPC to 1.36.1.

## [0.4.4](https://github.com/scalapb/zio-grpc/compare/v0.4.3...v0.4.4) - 2021-02-28

* Cross-build the code generator for Scala 2.12 and 2.13 using sbt-projectmatrix.
* Resolve deprecation warnings and fix code-generator assembly.
* Update gRPC to 1.36.0.

## [0.4.3](https://github.com/scalapb/zio-grpc/compare/v0.4.2...v0.4.3) - 2021-02-15

* Allocate a new gRPC call on each effect execution instead of capturing a stateful call instance ([#174](https://github.com/scalapb/zio-grpc/issues/174)).
* Fix server-streaming client behavior.
* Update ZIO to 1.0.4-2 and gRPC to 1.35.0.

## [0.4.2](https://github.com/scalapb/zio-grpc/compare/v0.4.1...v0.4.2) - 2020-12-08

* Add `ZTransform.andThen` for composing service transformations.
* Add a no-argument constructor for generated `Accessors`.
* Fix client-streaming calls with infinite input streams ([#166](https://github.com/scalapb/zio-grpc/issues/166)).
* Add service-decoration documentation and a database-backed Greeter example.
* Update gRPC to 1.34.0.

## [0.4.1](https://github.com/scalapb/zio-grpc/compare/v0.4.0...v0.4.1) - 2020-10-31

* Support per-call `CallOptions` and deadlines.
* Allow changing client metadata and obtaining per-request metadata from the environment.
* Add `transform` to `TransformServiceOps`.
* Add Scala.js documentation and an example of per-request metadata.
* Update ZIO to 1.0.3 and gRPC to 1.33.1.

## [0.4.0](https://github.com/scalapb/zio-grpc/compare/v0.3.0...v0.4.0) - 2020-08-14

This summary includes the 0.4.0 release candidates listed below.

* Upgrade to stable ZIO 1.0.0.
* Add Scala.js server-streaming support.
* Introduce `ServerMain` and `ServiceList` to simplify server setup, including `ServiceList.provideLayer`.
* Simplify generated service code and replace `ZBindableService.Aux[R, S]` with `ZBindableService[R, S]`.
* Separate client-streaming client and server traits to preserve the request stream's environment type.
* Add `fromBuilder` server constructors and deprecate the older `zmanaged` constructor.
* Publish the documentation website with installation, context, and generated-code guides.
* Fix a race between queue shutdown and incoming messages.

## [0.4.0-RC3](https://github.com/scalapb/zio-grpc/compare/v0.4.0-RC2...v0.4.0-RC3) - 2020-08-04

* Upgrade to ZIO 1.0.0.
* Make `ZGeneratedService` contravariant in its environment and context types.
* Correct server-layer error signatures to `Throwable` and replace `Combinable` with `Has.Union`.
* Expand the documentation and correct installation instructions.

## [0.4.0-RC2](https://github.com/scalapb/zio-grpc/compare/v0.4.0-RC1...v0.4.0-RC2) - 2020-07-19

* Separate client-streaming client and server traits so client streams can carry environment requirements.
* Add `fromBuilder` server constructors and deprecate the remaining `zmanaged` constructor.
* Introduce the documentation website and build examples in CI.

## [0.4.0-RC1](https://github.com/scalapb/zio-grpc/compare/v0.3.0...v0.4.0-RC1) - 2020-07-11

* Add server-streaming support on Scala.js.
* Introduce `ServerMain`, `ServiceList`, and helpers for creating servers.
* Simplify generated code and replace `ZBindableService.Aux[R, S]` with `ZBindableService[R, S]`.
* Move the standalone generator to ScalaPB's `protocgen` infrastructure and fix cross-version support.
* Update ZIO to 1.0.0-RC21-2 and ScalaPB to 0.10.7.

## [0.3.0](https://github.com/scalapb/zio-grpc/compare/v0.2.0...v0.3.0) - 2020-05-27

* Add initial Scala.js support.
* Add client interceptors and `SafeMetadata` on clients and servers.
* Introduce `RequestContext` for inspecting the server call from the environment.
* Support multiple services, passing context through the environment, and environment-dependent client layers.
* **Breaking:** remove the `WithContext` and `WithMeta` APIs as part of the context and metadata redesign.
* Fix Scala 2.12 compilation and update ZIO to 1.0.0-RC20.

## [0.2.0](https://github.com/scalapb/zio-grpc/compare/v0.1.0...v0.2.0) - 2020-03-30

* Add service context support.
* Replace deprecated `ZLayer.NoDeps` usage with `Layer`.
* Update ZIO to 1.0.0-RC18-2 and ScalaPB to 0.10.2.

## [0.1.0](https://github.com/scalapb/zio-grpc/commits/v0.1.0) - 2020-03-08

Initial release, covering development from the first commit on 2020-01-29.

* Generate ZIO services and clients from Protocol Buffer definitions using ScalaPB.
* Support unary, client-streaming, server-streaming, and bidirectional RPCs with ZIO streams.
* Propagate client interruption to the server to cancel requests.
* Integrate generated services and server construction with `ZLayer`.
* Publish the standalone `protoc-gen-zio` generator and use the `scalapb.zio_grpc` package.
* Include examples and end-to-end tests; use ZIO 1.0.0-RC18 and ScalaPB 0.10.0.
