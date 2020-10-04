scalaVersion := "2.13.1"

resolvers += Resolver.sonatypeRepo("snapshots")

val grpcVersion = "1.30.1"

PB.targets in Compile := Seq(
  scalapb.gen(grpc = true) -> (sourceManaged in Compile).value,
  scalapb.zio_grpc.ZioCodeGenerator -> (sourceManaged in Compile).value
)

libraryDependencies ++= Seq(
  "io.grpc" % "grpc-netty" % grpcVersion,
  "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion,
  "com.thesamet.scalapb" %% "scalapb-json4s" % "0.10.1"
)

run / fork := true
