addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.6.2")

addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.1.0-RC2")

val zioGrpcVersion = sys.props.getOrElse("zio.grpc.version", "0.6.3")

libraryDependencies ++= Seq(
  "com.thesamet.scalapb.zio-grpc" %% "zio-grpc-codegen" % zioGrpcVersion,
  "com.thesamet.scalapb" %% "compilerplugin" % "0.11.21"
)
