import Settings.stdSettings

val grpcVersion = "1.28.0"

val Scala213 = "2.13.1"

val Scala212 = "2.12.10"

ThisBuild / resolvers += Resolver.sonatypeRepo("snapshots")

ThisBuild / scalaVersion := Scala213

ThisBuild / crossScalaVersions := Seq(Scala212, Scala213)

skip in publish := true

sonatypeProfileName := "com.thesamet"

inThisBuild(
  List(
    organization := "com.thesamet.scalapb.zio-grpc",
    homepage := Some(url("https://github.com/scalameta/sbt-scalafmt")),
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

val zioVersion = "1.0.0-RC18"

lazy val core = project
  .in(file("core"))
  .settings(stdSettings)
  .settings(
    name := "zio-grpc-core",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % zioVersion,
      "dev.zio" %% "zio-streams" % zioVersion,
      "io.grpc" % "grpc-services" % grpcVersion,
      "dev.zio" %% "zio-test" % zioVersion % "test",
      "dev.zio" %% "zio-test-sbt" % zioVersion % "test"
    )
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

def projDef(name: String, shebang: Boolean) =
  sbt
    .Project(name, new File(name))
    .enablePlugins(AssemblyPlugin)
    .dependsOn(codeGen)
    .settings(stdSettings)
    .settings(
      assemblyOption in assembly := (assemblyOption in assembly).value.copy(
        prependShellScript = Some(
          sbtassembly.AssemblyPlugin.defaultUniversalScript(shebang = shebang)
        )
      ),
      skip in publish := true,
      Compile / mainClass := Some("scalapb.zio_grpc.ZioCodeGenerator")
    )

lazy val protocGenZioUnix = projDef("protoc-gen-zio-unix", shebang = true)

lazy val protocGenZioWindows =
  projDef("protoc-gen-zio-windows", shebang = false)

lazy val protocGenZio = project
  .settings(
    crossScalaVersions := List(Scala213),
    name := "protoc-gen-zio",
    publishArtifact in (Compile, packageDoc) := false,
    publishArtifact in (Compile, packageSrc) := false,
    crossPaths := false,
    addArtifact(
      Artifact("protoc-gen-zio", "jar", "sh", "unix"),
      assembly in (protocGenZioUnix, Compile)
    ),
    addArtifact(
      Artifact("protoc-gen-zio", "jar", "bat", "windows"),
      assembly in (protocGenZioWindows, Compile)
    ),
    autoScalaLibrary := false
  )

lazy val e2e = project
  .in(file("e2e"))
  .dependsOn(core)
  .settings(stdSettings)
  .settings(
    skip in publish := true,
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-test" % zioVersion % "test",
      "dev.zio" %% "zio-test-sbt" % zioVersion % "test",
      "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion,
      "io.grpc" % "grpc-netty" % grpcVersion
    ),
    Compile / PB.generate := ((Compile / PB.generate) dependsOn (protocGenZioUnix / Compile / assembly)).value,
    PB.targets in Compile := Seq(
      scalapb.gen(grpc = true) -> (sourceManaged in Compile).value,
      (
        PB.gens.plugin(
          "zio",
          (protocGenZioUnix / assembly / target).value / "protoc-gen-zio-unix-assembly-" + version.value + ".jar"
        ),
        Seq()
      ) -> (Compile / sourceManaged).value
    ),
    Compile / PB.recompile := true, // always regenerate protos, not cache
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )
