# A simple integration project with JavaScript

This runs a backend server with `zio-grpc` example server and uses a `grpc-web` JavaScript library to talk to it from the web browser

## Setup

- Back - zio-grpc
- Front - [grpc-web](https://github.com/grpc/grpc-web)
- Proxy - [Envoy](https://www.envoyproxy.io/)

The reason to use a proxy is that grpc is built on the `HTTP 2.0` layer, while all modern browsers still work on `HTTP 1.1`.
The good news is that the `zio-grpc` won't need any change at time when browsers add `HTTP 2.0` support, since apps would talk to the back without a proxy

Envoy is a modern lean and a high performance proxy, which natively supports `grpc` thus being a natural choice for many `grpc` backends

## Installation

1. Run `zio-grpc` server

```bash
cd examples
sbt `runMain zio_grpc.examples.helloworld.HelloWorldServer`
```

2. Build the JavaScript client: `make build`

3. Launch the `Envoy` proxy: `make proxy`

4. Launch the client: `make run`

5. Open the web browser with `localhost:8081` and check the server response in the browser console
