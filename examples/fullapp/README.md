# Full App Demo

This project demonstrates a zio-grpc server working with a Scala.js client.
The shared protos reside in an SBT subproject named `protos`.

Requests reaching the server will be handled by envoyproxy on port 8080. The
requests will be proxied to a gRPC server running on port 9000.

To get this project running:

1. In SBT, build the client:

   ```
   sbt:fullapp> webapp/fastOptJS/webpack
   ```

2. Run the gRPC server:

   ```
   sbt:fullapp> server/runMain examples.ExampleServer
   ```

3. On a different tab, start envoyproxy in a docker container:

   ```
   $ docker compose up
   ```

4. In a browser open `index.html` in this directory. In Google Chrome, you can open a file by pressing
   Ctrl-O in Windows or Linux, or Cmd-O on a Mac.

5. The page should be blank, however if you open `Developer Tools > Console`,
   you can see the output of the client program.
