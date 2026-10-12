ThisBuild / scalaVersion := "2.13.18"

ThisBuild / cancelable := true

ThisBuild / connectInput := true

val grpcVersion = "1.72.0"

lazy val protos = crossProject(JSPlatform, JVMPlatform)
  .in(file("protos"))
  .settings(
    Compile / PB.targets := Seq(
      scalapb.gen(grpc = true) -> (Compile / sourceManaged).value,
      scalapb.zio_grpc.ZioCodeGenerator -> (Compile / sourceManaged).value
    ),
    Compile / PB.protoSources := Seq(
      (ThisBuild / baseDirectory).value / "protos" / "src" / "main" / "protobuf"
    )
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      "com.thesamet.scalapb" %%% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion
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
      "io.github.cquiroz" %%% "scala-java-time" % "2.7.0"
    ),
    Compile / npmDependencies += "grpc-web" -> "2.1.1",
    Compile / npmResolutions += "grpc-web" -> "2.1.1",
    Test / npmResolutions += "grpc-web" -> "2.1.1",
    webpack / version := "5.111.1",
    webpackCliVersion := "7.2.3",
    startWebpackDevServer / version := "6.0.0",
    scalaJSUseMainModuleInitializer := true
  )
