scalaVersion := "2.13.4"

resolvers += Resolver.sonatypeRepo("snapshots")

val grpcVersion = "1.37.1"

Compile / PB.targets := Seq(
  scalapb.gen(grpc = true) -> (Compile / sourceManaged).value,
  scalapb.zio_grpc.ZioCodeGenerator -> (Compile / sourceManaged).value
)

libraryDependencies ++= Seq(
  "io.grpc" % "grpc-netty" % grpcVersion,
  "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion,
  "com.thesamet.scalapb" %% "scalapb-json4s" % "0.11.0"
)

run / fork := true
