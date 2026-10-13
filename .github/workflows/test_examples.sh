#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."

# A private version ensures examples cannot silently resolve a published release.
export ZIO_GRPC_TEST_VERSION="0.0.0-local-$(git rev-parse --short=12 HEAD)-SNAPSHOT"
sbt 'set ThisBuild / version := sys.env("ZIO_GRPC_TEST_VERSION")' \
  coreJVM2_13/publishLocal coreJS2_13/publishLocal codeGenJVM2_12/publishLocal

for example in helloworld routeguide fullapp; do
  echo "Testing examples/$example against $ZIO_GRPC_TEST_VERSION"
  (cd "examples/$example" && sbt "-Dzio.grpc.version=$ZIO_GRPC_TEST_VERSION" \
    scalafmtCheck Test/scalafmtCheck scalafmtSbtCheck test)
done
(cd examples/fullapp && sbt "-Dzio.grpc.version=$ZIO_GRPC_TEST_VERSION" webapp/fastOptJS/webpack)
