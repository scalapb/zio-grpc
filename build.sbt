import Settings.stdSettings

val grpcVersion = "1.30.2"

val Scala213 = "2.13.2"

val Scala212 = "2.12.10"

ThisBuild / resolvers += Resolver.sonatypeRepo("snapshots")

ThisBuild / scalaVersion := Scala213

ThisBuild / crossScalaVersions := Seq(Scala212, Scala213)

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

val zioVersion = "1.0.0-RC21"

lazy val core = crossProject(JSPlatform, JVMPlatform)
  .in(file("core"))
  .settings(stdSettings)
  .settings(
    name := "zio-grpc-core",
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio" % zioVersion,
      "dev.zio" %%% "zio-streams" % zioVersion,
      "dev.zio" %%% "zio-test" % zioVersion % "test",
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
      "io.github.cquiroz" %%% "scala-java-time" % "2.0.0" % "test"
    ),
    npmDependencies in Compile += "grpc-web" -> "1.0.7"
  )

lazy val codeGen = project
  .in(file("code-gen"))
  .enablePlugins(BuildInfoPlugin)
  .settings(stdSettings)
  .settings(
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
  .settings(stdSettings)
  .settings(
    skip in publish := true,
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-test" % zioVersion % "test",
      "dev.zio" %% "zio-test-sbt" % zioVersion % "test",
      "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion,
      "io.grpc" % "grpc-netty" % grpcVersion
    ),
    protocGenZio.addDependency,
    PB.targets in Compile := Seq(
      scalapb.gen(grpc = true) -> (sourceManaged in Compile).value,
      (
        protocGenZio.plugin.value,
        Seq()
      ) -> (Compile / sourceManaged).value
    ),
    Compile / PB.recompile := true, // always regenerate protos, not cache
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )
