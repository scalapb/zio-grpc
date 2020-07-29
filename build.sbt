import Settings.stdSettings

val grpcVersion = "1.31.0"

val Scala213 = "2.13.3"

val Scala212 = "2.12.11"

ThisBuild / resolvers += Resolver.sonatypeRepo("snapshots")

ThisBuild / scalaVersion := Scala212

skip in publish := true

sonatypeProfileName := "com.thesamet"

inThisBuild(
  List(
    organization := "com.thesamet.scalapb.zio-grpc",
    homepage := Some(url("https://github.com/scalapb/zio-grpc")),
    licenses := List(
      "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")
    ),
    developers := List(
      Developer(
        "thesamet",
        "Nadav Samet",
        "thesamet@gmail.com",
        url("https://www.thesamet.com")
      )
    )
  )
)

val zioVersion = "1.0.0-RC21-2"

lazy val core = crossProject(JSPlatform, JVMPlatform)
  .in(file("core"))
  .settings(stdSettings)
  .settings(
    crossScalaVersions := Seq(Scala212, Scala213),
    name := "zio-grpc-core",
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio"          % zioVersion,
      "dev.zio" %%% "zio-streams"  % zioVersion,
      "dev.zio" %%% "zio-test"     % zioVersion % "test",
      "dev.zio" %%% "zio-test-sbt" % zioVersion % "test"
    )
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      "io.grpc" % "grpc-services" % grpcVersion
    )
  )
  .jsConfigure(
    _.enablePlugins(ScalaJSBundlerPlugin)
  )
  .jsSettings(
    libraryDependencies ++= Seq(
      "com.thesamet.scalapb.grpcweb" %%% "scalapb-grpcweb" % "0.4.1",
      "io.github.cquiroz"            %%% "scala-java-time" % "2.0.0" % "test"
    ),
    npmDependencies in Compile += "grpc-web" -> "1.0.7"
  )

lazy val codeGen = project
  .in(file("code-gen"))
  .enablePlugins(BuildInfoPlugin)
  .settings(stdSettings)
  .settings(
    scalaVersion := Scala212,
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "scalapb.zio_grpc",
    name := "zio-grpc-codegen",
    libraryDependencies ++= Seq(
      "com.thesamet.scalapb" %% "compilerplugin" % scalapb.compiler.Version.scalapbVersion
    )
  )

lazy val protocGenZio = protocGenProject("protoc-gen-zio", codeGen)
  .settings(
    Compile / mainClass := Some("scalapb.zio_grpc.ZioCodeGenerator")
  )

lazy val e2e = project
  .in(file("e2e"))
  .dependsOn(core.jvm)
  .enablePlugins(LocalCodeGenPlugin)
  .settings(stdSettings)
  .settings(
    crossScalaVersions := Seq(Scala212, Scala213),
    skip in publish := true,
    libraryDependencies ++= Seq(
      "dev.zio"              %% "zio-test"             % zioVersion % "test",
      "dev.zio"              %% "zio-test-sbt"         % zioVersion % "test",
      "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion,
      "io.grpc"               % "grpc-netty"           % grpcVersion
    ),
    PB.targets in Compile := Seq(
      scalapb.gen(grpc = true) -> (sourceManaged in Compile).value,
      genModule(
        "scalapb.zio_grpc.ZioCodeGenerator$"
      )                        -> (sourceManaged in Compile).value
    ),
    codeGenClasspath := (codeGen / Compile / fullClasspath).value,
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )

lazy val docs = project
  .enablePlugins(LocalCodeGenPlugin)
  .in(file("zio-grpc-docs"))
  .dependsOn(core.jvm)
  .settings(
    crossScalaVersions := Seq(Scala212),
    skip in publish := true,
    moduleName := "zio-grpc-docs",
    mdocVariables := Map(
      "sbtProtocVersion" -> "0.99.34",
      "grpcVersion"      -> "1.31.0",
      "zioGrpcVersion"   -> "0.4.0-RC2",
      "scalapbVersion"   -> scalapb.compiler.Version.scalapbVersion
    ),
    libraryDependencies ++= Seq(
      "io.grpc"               % "grpc-netty"           % grpcVersion,
      "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion
    ),
    PB.targets in Compile := Seq(
      scalapb.gen(grpc = true) -> (sourceManaged in Compile).value,
      genModule(
        "scalapb.zio_grpc.ZioCodeGenerator$"
      )                        -> (sourceManaged in Compile).value
    ),
    codeGenClasspath := (codeGen / Compile / fullClasspath).value
  )
  .enablePlugins(MdocPlugin, DocusaurusPlugin)
