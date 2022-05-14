resolvers += Resolver.sonatypeRepo("snapshots")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.3")

addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.2")

val zioGrpcVersion = "0.5.1"

libraryDependencies ++= Seq(
  "com.thesamet.scalapb.zio-grpc" %% "zio-grpc-codegen" % zioGrpcVersion,
  "com.thesamet.scalapb"          %% "compilerplugin"   % "0.11.10"
)
