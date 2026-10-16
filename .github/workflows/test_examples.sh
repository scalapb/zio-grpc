#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."

# A private version ensures examples cannot silently resolve a published release.
export ZIO_GRPC_TEST_VERSION="0.0.0-local-$(git rev-parse --short=12 HEAD)-SNAPSHOT"
sbt 'set every version := sys.env("ZIO_GRPC_TEST_VERSION"); coreJVM2_13/publishLocal; coreJS2_13/publishLocal; codeGenJVM3/publishLocal'

for example in helloworld routeguide fullapp; do
  echo "Testing examples/$example against $ZIO_GRPC_TEST_VERSION"
  (cd "examples/$example" && sbt "-Dzio.grpc.version=$ZIO_GRPC_TEST_VERSION" \
    "scalafmtCheck; Test/scalafmtCheck; scalafmtSbtCheck; testFull")
done
(cd examples/fullapp && sbt "-Dzio.grpc.version=$ZIO_GRPC_TEST_VERSION" webapp/fastLinkJS && npm ci && npm run bundle -- \
  --entry ./webapp/target/main/main.js --output-path "$PWD/webapp/target/bundle")
