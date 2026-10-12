resolvers ++= Resolver.sonatypeOssRepos("snapshots")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.6")

addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.7")

val zioGrpcVersion = "0.6.2"

libraryDependencies ++= Seq(
  "com.thesamet.scalapb.zio-grpc" %% "zio-grpc-codegen" % zioGrpcVersion,
  "com.thesamet.scalapb" %% "compilerplugin" % "0.11.15"
)
