---
title: Backpressure
sidebar_label: Backpressure
custom_edit_url: https://github.com/scalapb/zio-grpc/edit/master/docs/backpressure.md
---

From version 0.5.3 onwards, zio-grpc provides backpressure support for server
streaming RPCs. In case the call is not capable to sending additional messages
without buffering (as determined by [`ServerCall.isReady`](https://grpc.github.io/grpc-java/javadoc/io/grpc/ServerCall.html#isReady])), sending messages from the queue associated with server response `Stream` will stop. The default size of this queue is `16`,  and can be configured by setting the system property `zio_grpc.backpressure_queue_size` or the environment variable `ZIO_GRPC_BACKPRESSURE_QUEUE_SIZE`. Setting the value to `0` or a negative number will disable buffering but keep the back pressure at the chunk level (`isReady` will be checked after processing each chunk instead of each message).
