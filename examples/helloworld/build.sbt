scalaVersion := "2.13.10"

resolvers ++= Resolver.sonatypeOssRepos("snapshots")

val grpcVersion = "1.50.1"

Compile / PB.targets := Seq(
  scalapb.gen(grpc = true) -> (Compile / sourceManaged).value,
  scalapb.zio_grpc.ZioCodeGenerator -> (Compile / sourceManaged).value
)

libraryDependencies ++= Seq(
  "io.grpc" % "grpc-netty" % grpcVersion,
  "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion
)

run / fork := true
