---
title: Introduction
---

ZIO-gRPC lets you write purely functional gRPC servers and clients using ZIO.

## Highlights
* Supports all types of RPCs (unary, client streaming, server streaming, bidirectional).
* Uses ZIO's `ZStream` to easily implement streaming requests.
* Cancellable RPCs: client-side ZIO cancellations are propagated to the server to abort the request and save resources.
