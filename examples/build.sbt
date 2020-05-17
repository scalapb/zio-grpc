ThisBuild / resolvers += Resolver.sonatypeRepo("snapshots")

ThisBuild / scalaVersion := "2.13.1"

ThisBuild / cancelable in Global := true

ThisBuild / connectInput := true

val grpcVersion = "1.29.0"

lazy val protos = crossProject(JSPlatform, JVMPlatform)
  .in(file("protos"))
  .settings(
    PB.targets in Compile := Seq(
      scalapb.gen(grpc = true) -> (sourceManaged in Compile).value,
      scalapb.zio_grpc.ZioCodeGenerator -> (sourceManaged in Compile).value,
    ),
    PB.protoSources in Compile := Seq(
        (baseDirectory in ThisBuild).value / "protos" / "src" / "main" / "protobuf"
    ),
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      "com.thesamet.scalapb" %%% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion,
    )
  )
  .jsSettings(
    libraryDependencies ++= Seq(
      "com.thesamet.scalapb.grpcweb" %%% "scalapb-grpcweb" % "0.2.0+20-3b251475+20200503-2052-SNAPSHOT",
    )
  )

lazy val server = project
  .dependsOn(protos.jvm)
  .settings(
    libraryDependencies ++= Seq(
      "io.grpc" % "grpc-netty" % grpcVersion
    ),
    fork := true
  )

lazy val client = project
  .dependsOn(protos.jvm)
  .settings(
    libraryDependencies ++= Seq(
      "io.grpc" % "grpc-netty" % grpcVersion
    ),
    fork := true
  )

lazy val webapp = project
  .enablePlugins(ScalaJSPlugin)
  .enablePlugins(ScalaJSBundlerPlugin)
  .dependsOn(protos.js)
  .settings(
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) },
    libraryDependencies ++= Seq(
      "io.github.cquiroz" %%% "scala-java-time" % "2.0.0"
    ),
    scalaJSUseMainModuleInitializer := true
  )

