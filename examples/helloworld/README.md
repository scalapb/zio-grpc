To test the server:

    sbt "runMain zio_grpc.examples.helloworld.HelloWorldServer"

On another shell:

    sbt "runMain zio_grpc.examples.helloworld.HelloWorldClient"
